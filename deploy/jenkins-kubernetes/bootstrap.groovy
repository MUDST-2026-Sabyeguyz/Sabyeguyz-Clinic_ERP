import groovy.json.JsonSlurper
import hudson.model.Node
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.JNLPLauncher
import jenkins.model.Jenkins
import hudson.util.Secret
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def controller = Jenkins.get()
def config = new JsonSlurper().parse(new File('/var/jenkins_home/clinic-kubernetes-bootstrap.json'))
controller.setNumExecutors(0)
def agent = controller.getNode('clinic-present')
if (agent != null && agent.getRemoteFS() != (config.agentRoot as String)) {
    controller.removeNode(agent)
    agent = null
}
if (agent == null) {
    agent = new DumbSlave('clinic-present', config.agentRoot as String, new JNLPLauncher())
    controller.addNode(agent)
}
agent.setNumExecutors(1)
agent.setLabelString('clinic-present')
agent.setMode(Node.Mode.EXCLUSIVE)
agent.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).each {
    agent.getNodeProperties().remove(it)
}
agent.getNodeProperties().add(new EnvironmentVariablesNodeProperty(
        new EnvironmentVariablesNodeProperty.Entry('CLINIC_SOURCE_REPO', config.repoPath as String),
        new EnvironmentVariablesNodeProperty.Entry('CLINIC_SOURCE_BRANCH', config.sourceBranch as String)))
agent.save()

def kubeconfigFile = new File('/var/jenkins_home/clinic-local-kubeconfig.json')
if (!kubeconfigFile.isFile()) throw new IllegalStateException('The namespace-scoped kubeconfig is missing')
def credentialProvider = SystemCredentialsProvider.getInstance()
def credentialStore = credentialProvider.getStore()
def globalDomain = Domain.global()
credentialStore.getCredentials(globalDomain).findAll { it.id == 'clinic-local-kubeconfig' }.each {
    credentialStore.removeCredentials(globalDomain, it)
}
credentialStore.addCredentials(globalDomain, new StringCredentialsImpl(CredentialsScope.GLOBAL,
        'clinic-local-kubeconfig', 'Namespace-scoped local Kubernetes deployer credential',
        Secret.fromString(kubeconfigFile.text)))
credentialProvider.save()

def job = controller.getItem('Clinic-Kubernetes')
if (job != null && !(job instanceof WorkflowJob)) {
    throw new IllegalStateException('Clinic-Kubernetes already exists and is not a Pipeline job')
}
if (job == null) job = controller.createProject(WorkflowJob, 'Clinic-Kubernetes')
job.setDefinition(new CpsFlowDefinition(new File('/var/jenkins_home/clinic-kubernetes.Jenkinsfile').text, true))
job.save()

def secretFile = new File('/var/jenkins_home/secrets/clinic-present-agent')
secretFile.text = agent.toComputer().getJnlpMac()
secretFile.setReadable(false, false)
secretFile.setWritable(false, false)
secretFile.setReadable(true, true)
secretFile.setWritable(true, true)
controller.save()
if (job.getLastBuild() == null) job.scheduleBuild2(10)
println("Clinic-Kubernetes monitors ${config.sourceBranch}; connect the clinic-present Windows agent.")
