import jenkins.model.Jenkins
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import jenkins.security.ApiTokenProperty
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import hudson.model.*
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl

def instance = Jenkins.get()
def location = jenkins.model.JenkinsLocationConfiguration.get()
location.setUrl('http://localhost:8086/')
location.save()
if (!instance.isUseSecurity()) {
    def realm = new HudsonPrivateSecurityRealm(false)
    def password = new File('/opt/clinic/ci/runtime/admin-password').text.trim()
    def admin = realm.createAccount('clinic-admin', password)
    instance.setSecurityRealm(realm)
    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    instance.setAuthorizationStrategy(strategy)
    def token = admin.getProperty(ApiTokenProperty).tokenStore.generateNewToken('local-ci-control')
    def file = new File('/var/jenkins_home/clinic-api-token')
    file.text = token.plainValue
    file.setReadable(false, false)
    file.setReadable(true, true)
    admin.save()
    instance.save()
}
instance.setNumExecutors(2)
def credentials = SystemCredentialsProvider.getInstance()
if (!credentials.credentials.any { it.id == 'clinic-github' }) {
    def tokenFile = new File('/opt/clinic/ci/runtime/github-token')
    if (tokenFile.exists()) {
        credentials.credentials.add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, 'clinic-github', 'GitHub access for CI checkout and commit status', 'x-access-token', tokenFile.text.trim()))
        credentials.save()
    }
}
def job = instance.getItem('Clinic-CI') ?: instance.createProject(WorkflowJob, 'Clinic-CI')
job.setDefinition(new CpsFlowDefinition(new File('/opt/clinic/ci/Replay.Jenkinsfile').text, true))
job.removeProperty(ParametersDefinitionProperty)
job.addProperty(new ParametersDefinitionProperty([
    new StringParameterDefinition('SOURCE_SHA', '', 'Exact 40-character commit SHA; required for historical replay'),
    new StringParameterDefinition('WORKFLOWS', '', 'Comma-separated workflow filenames; blank runs every workflow at this SHA')
]))
job.save()
def watcher = instance.getItem('Clinic-Watch') ?: instance.createProject(WorkflowJob, 'Clinic-Watch')
watcher.setDefinition(new CpsFlowDefinition(new File('/opt/clinic/ci/Watch.Jenkinsfile').text, true))
watcher.save()
instance.save()
