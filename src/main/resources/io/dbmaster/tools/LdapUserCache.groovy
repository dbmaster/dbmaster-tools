package io.dbmaster.tools

import groovy.json.StringEscapeUtils
import groovy.sql.Sql
import io.dbmaster.tools.login.audit.*

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.CancellationException
import java.util.regex.Matcher
import java.util.regex.Pattern

import javax.naming.*
import javax.naming.directory.*
import javax.naming.ldap.*

import org.slf4j.Logger

import com.branegy.dbmaster.connection.ConnectionProvider
import com.branegy.dbmaster.connection.JdbcConnector
import com.branegy.dbmaster.custom.CustomFieldConfig
import com.branegy.dbmaster.custom.CustomObjectEntity
import com.branegy.dbmaster.custom.CustomObjectService
import com.branegy.dbmaster.custom.CustomObjectTypeEntity
import com.branegy.dbmaster.custom.CustomFieldConfig.Type
import com.branegy.dbmaster.custom.field.server.api.ICustomFieldService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.util.NameMap
import com.branegy.scripting.DbMaster
import com.branegy.service.connection.api.ConnectionService
import com.branegy.service.core.QueryRequest

public class LdapUserCache { 
    
    private DbMaster dbm
    private Logger logger
    
    public NameMap ldapAccountByDN   = new NameMap()
    public NameMap ldapAccountByName = new NameMap()

    public LdapUserCache(DbMaster dbm, Logger logger) {
        this.dbm = dbm
        this.logger = logger
    }
     
    public void loadLdapAccounts(connectionSrv) {
        def ldapConns = connectionSrv.getConnectionList().findAll { it.driver=="ldap" }
        ldapConns.each { connection->
            logger.info("Loading users and group from  ${connection.name}")
            def ldapSearch = new LdapSearch(dbm, logger)    
            def ldapQuery  = "(|(objectClass=user)(objectClass=group))"
            // def ldap_attributes = "member;objectClass;sAMAccountName;distinguishedName;description;memberOf;name"
            def ldapAttributes = "member;memberOf;sAMAccountName;distinguishedName;name;userAccountControl"
            logger.info("Retrieving ldap accounts and groups")
            
            String ldapContext = "";
            String domain = "";
            connection.properties.each { p->
                if (p.key == "defaultContext") {
                    ldapContext = p.value
                    logger.info("Found context = ${ldapContext}")
                }
                if (p.key == "domain") { 
                     domain = p.value
                     logger.info("Domain = ${domain}")
                }
            }
            
            def search_results = ldapSearch.search(connection.name, ldapContext, ldapQuery, ldapAttributes)
            
            def getAll = { ldapAttribute ->
                def result = null
                if (ldapAttribute!=null) {
                    result = []
                    def values = ldapAttribute.getAll()
                    while (values.hasMore()) {
                        result.add(values.next().toString())
                    }
                }
                return result
            }
            
            logger.info("Found ${search_results.size()} records")
            
            search_results.each { result_item ->  
                Attributes attributes = result_item.getAttributes()
                def name = attributes.get("sAMAccountName")?.get()
                def dn = attributes.get("distinguishedName")?.get()
                def members = getAll(attributes.get("member"))
                def member_of = getAll(attributes.get("memberOf"))
                // def domain = attributes.get("msSFU30NisDomain")?.get()
                // 
                def userAccountControl = attributes.get("userAccountControl")?.get()
                def title = attributes.get("name")?.get();
                
                // def object_class = getAll(attributes.get("objectClass"))
                
                def account = [ "name" : name, "dn" : dn, "members" : members, 
                                "member_of" : member_of, "title": title, 
                                "disabled" : userAccountControl, "domain": domain]
                // logger.debug(account.toString());
                ldapAccountByDN[dn] = account
                ldapAccountByName[domain+"\\"+name] = account
            }
        }
     /*
        def xs = new com.thoughtworks.xstream.XStream()
        
          
        logger.info("Loading accounts by DN")
        def fr = new java.io.BufferedReader(new FileReader("ldapAccountByDN.xml"))
        ldapAccountByDN = xs.fromXML(fr)
        fr.close()
        
        ldapAccountByDN.values().each { acc ->
            ldapAccountByName[acc.domain+"\\"+acc.name] = acc
        }
        logger.info("Done")
/*
    logger.info("Loading accounts by name")        
        fr = new java.io.BufferedReader(new FileReader("ldapAccountByName.xml"))
        ldapAccountByName = xs.fromXML(fr)
        fr.close()
logger.info("done")
*/     /* 

        def pw = new java.io.PrintWriter("ldapAccountByDN.xml")
        xs.toXML(ldapAccountByDN, pw)
        pw.close()
      
        pw = new java.io.PrintWriter("ldapAccountByName.xml")
        xs.toXML(ldapAccountByName, pw)
        pw.close()
    */
    }
    
    public List<String> getSubGroups (List<String> list, account) {
        account.member_of.each { member_of_dn ->
            def group = ldapAccountByDN[member_of_dn]
            if (group == null) {
                logger.debug("Account for ${member_of_dn} does not exist")
            } else {
                def groupName = group.name
                if (!list.contains(groupName)) {
                    list.add(groupName)
                    getSubGroups(list,group)
                }
            }
        }
        return list
    }

}