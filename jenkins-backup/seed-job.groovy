import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition

def instance = Jenkins.get()
if (true) {
    def job = instance.getItem('Clinic-backup') ?: instance.createProject(WorkflowJob, 'Clinic-backup')
    job.setDefinition(new CpsFlowDefinition(new File('/opt/clinic/Jenkinsfile').text, true))
    job.save()
}
