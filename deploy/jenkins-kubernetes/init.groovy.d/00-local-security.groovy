import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.security.HudsonPrivateSecurityRealm
import hudson.model.User
import jenkins.model.Jenkins

def instance = Jenkins.get()
def password = System.getenv('CLINIC_JENKINS_ADMIN_PASSWORD')
if (!password) throw new IllegalStateException('Local Jenkins admin password secret is empty')

def realm = instance.getSecurityRealm()
if (!(realm instanceof HudsonPrivateSecurityRealm)) realm = new HudsonPrivateSecurityRealm(false)
def admin = User.getById('admin', false)
if (admin == null) realm.createAccount('admin', password)
else admin.addProperty(HudsonPrivateSecurityRealm.Details.fromPlainPassword(password))
instance.setSecurityRealm(realm)
def authorization = new FullControlOnceLoggedInAuthorizationStrategy()
authorization.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(authorization)
instance.save()
println('Configured localhost-only Jenkins authentication for the local administrator.')
