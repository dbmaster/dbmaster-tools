import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.dbmaster.testng.BaseToolTestNGCase;
import static org.testng.Assert.assertEquals;

import com.branegy.scripting.DbMaster;

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test

import com.branegy.scripting.DbMasterImpl
import com.branegy.tools.osgi.OsgiService;
import com.branegy.tools.osgi.OsgiToolBootstrapper;

public class DbMasterToolsIT extends BaseToolTestNGCase {
    
    @Parameters("username")
    @Test
    public void testCurrentUser(String userName) {
        URL url = getBundle().getEntry("/io/dbmaster/tools/DbmTools.groovy");
        Class cl =((GroovyClassLoader)getClass().getClassLoader()).parseClass(new GroovyCodeSource(url));
        
        DbMaster dbm = DbMasterImpl.getInstance(getInjector())
        Logger logger = LoggerFactory.getLogger(getClass());
        PrintWriter out = null;
        
        Constructor<?> c = cl.getDeclaredConstructor(Object.class,Object.class,Object.class);
        c.setAccessible(true);
        Object result = c.newInstance(dbm,logger,out);
        
        Method m = result.getClass().getMethod("getCurrentUser");
        m.setAccessible(true);
        Object currentUser = m.invoke(result);
        
        assertEquals(currentUser, userName); // compare actual user with parameter
    }
}
