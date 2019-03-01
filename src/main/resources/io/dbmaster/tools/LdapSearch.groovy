package io.dbmaster.tools

import javax.naming.*
import javax.naming.directory.*
import javax.naming.ldap.*

import com.branegy.service.connection.api.ConnectionService
import com.branegy.dbmaster.connection.ConnectionProvider

import com.branegy.scripting.DbMaster
import org.slf4j.Logger

public class LdapSearch {
    private final DbMaster dbm
    private final Logger logger
    
    public LdapSearch(DbMaster dbm, Logger logger) {
        this.dbm = dbm;
        this.logger = logger;
    }
    
    public List<SearchResult> search(String server, String searchBase, String searchQuery, String attributes) {
        def connectionSrv = dbm.getService(ConnectionService.class);
        def connectionInfo = connectionSrv.findByName(server)
        Dialect dialect = null;
        def context = null;
        try {
            dialect = ConnectionProvider.get().getDialect(connectionInfo);
            String connectionSearchBase = null
            int timeOut = -1
            
            connectionInfo.properties.each { p->
                if (p.key.equals("timeLimit") && p.value!=null) {
                    try {
                        timeOut = Integer.parseInt ( p.value )
                    } catch (NumberFormatException e) {
                        logger.error("Time limit should be a number. Got ${p.value} from connection setting")
                    }
                }
                if (p.key.equals("defaultContext")) {
                    connectionSearchBase = p.value;
                }
            }
            
            if (searchBase==null) {
                searchBase == connectionSearchBase;
            }
            if (searchBase == null) { 
                throw new RuntimeException("Define search base context")
            }
    
            def dirContext = dialect.getContext()
            context = new  InitialLdapContext(dirContext.getEnvironment(), null)
    
            SearchControls ctrl = new SearchControls()
            // a candidate for script parameter
            ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE)
            // a candidate for script parameter
            ctrl.setCountLimit(0)
            // a candidate for script parameter
            if (timeOut!=-1 ) {
                logger.debug("Using time limit = ${timeOut}")
                ctrl.setTimeLimit(timeOut) // 10 second == 10000 ms
            }
    
            int pageSize = 500
    
            context.setRequestControls( [ new PagedResultsControl(pageSize,Control.CRITICAL) ] as Control[] )
    
            if (attributes!=null && attributes.length()>0) {
                def attrIDs = attributes.split(";")
                ctrl.setReturningAttributes(attrIDs);
            }
    
            def result = [] 
            // int index = 0
            try {
                while (true) {
                    NamingEnumeration enumeration = context.search(searchBase, searchQuery, ctrl);
    
                    while (enumeration.hasMore()) {
                        // SearchResult result = ;
                        result.add((SearchResult) enumeration.next())
                    }
                    
                    Control[] responseControls = context.getResponseControls()
                    byte[] cookie = null;
                    if(responseControls!=null) {
                        responseControls.each { prrc ->
                            if (prrc instanceof PagedResultsResponseControl) {
                                cookie = prrc.getCookie();
                            }
                        }
                    }
                    
                    if(cookie==null) {
                        break;
                    } else {
                        logger.debug("Switching to next page")
                    }
    
                    // Re-activate paged results
                    context.setRequestControls( [ new PagedResultsControl(pageSize, cookie, Control.CRITICAL) ] as Control[])
                }
            } catch (SizeLimitExceededException e) {
                logger.error("Error:"+e.message)
                // for paging see
                // http://www.forumeasy.com/forums/thread.jsp?tid=115756126876&fid=ldapprof2&highlight=LDAP+Search+Paged+Results+Control
            }
            return result;
        } finally {
            com.branegy.util.IOUtils.closeQuietly(dialect);
            context.close();
        }
    }
}