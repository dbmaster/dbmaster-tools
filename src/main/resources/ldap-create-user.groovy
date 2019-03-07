import java.security.MessageDigest
import java.util.Hashtable
import javax.naming.*
import javax.naming.directory.*
import sun.misc.BASE64Encoder
import com.branegy.service.connection.api.ConnectionService
import com.branegy.dbmaster.connection.ConnectionProvider

connectionSrv = dbm.getService(ConnectionService.class)
connectionInfo = connectionSrv.findByName(p_server)
dialect = ConnectionProvider.get().getDialect(connectionInfo)
dbm.closeResourceOnExit(dialect)

def context = dialect.getContext()

def encryptLdapPassword(String algorithm, String password) {
        MessageDigest md = MessageDigest.getInstance(algorithm)
        md.update(password.getBytes("UTF-8"))
        return "{" + algorithm + "}" + (new BASE64Encoder()).encode(md.digest())
}

Attributes entryAttrs = new BasicAttributes(true)
            
def objectClass = new BasicAttribute("objectClass")

["top","posixAccount","shadowAccount","person",
 "organizationalPerson","inetOrgPerson"].each { objectClass.add(it) }
             
entryAttrs.put(objectClass)
entryAttrs.put(new BasicAttribute("uid", p_username))
entryAttrs.put(new BasicAttribute("cn", p_first_name+" "+p_last_name))
entryAttrs.put(new BasicAttribute("displayName", p_first_name+" "+p_last_name))
entryAttrs.put(new BasicAttribute("givenName", p_first_name))
entryAttrs.put(new BasicAttribute("loginShell", "/bin/bash"))
entryAttrs.put(new BasicAttribute("sn", p_last_name))
entryAttrs.put(new BasicAttribute("homeDirectory", "/home/"+p_username))
entryAttrs.put(new BasicAttribute("mail", p_email))
entryAttrs.put(new BasicAttribute("o", p_company))
entryAttrs.put(new BasicAttribute("uidNumber", "8231"))
entryAttrs.put(new BasicAttribute("gidNumber", "5000"))
            
            
entryAttrs.put(new BasicAttribute("userpassword", encryptLdapPassword("SHA", p_password)))
            
String name = "cn=${p_first_name}  ${p_last_name},${p_context}"

context.bind(name, context, entryAttrs)