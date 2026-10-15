import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import hudson.model.BooleanParameterDefinition
import hudson.model.ChoiceParameterDefinition
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition

def instance = Jenkins.get()
if (true) {
    def job = instance.getItem('Clinic-backup') ?: instance.createProject(WorkflowJob, 'Clinic-backup')
    job.setDefinition(new CpsFlowDefinition(new File('/opt/clinic/Jenkinsfile').text, true))
    job.removeProperty(ParametersDefinitionProperty)
    job.addProperty(new ParametersDefinitionProperty([
        new BooleanParameterDefinition('DEPLOY', false, 'Deploy to local test app after checks pass'),
        new BooleanParameterDefinition('LOCAL_SNAPSHOT', true, 'Use a local Git bundle'),
        new ChoiceParameterDefinition('SOURCE_BRANCH', ['develop', 'feat/backend-v2-integration'] as String[], 'Branch to test'),
        new StringParameterDefinition('GIT_CREDENTIAL_ID', 'github-read', 'Read-only Git credential')
    ]))
    job.save()
}
