import io.dbmaster.tools.LdapSearch

import javax.naming.*
import javax.naming.directory.*
import javax.naming.ldap.*


def ldapSearchResult = new LdapSearch(dbm,logger).search(p_server, p_base, p_query, p_attributes)

println "<table border=\"1\">"

int index = 0

ldapSearchResult.each { result_item ->  
    Attributes attribs = result_item.getAttributes()
    index=index+1
    println "<tr><td>${index}</td><td>"
    def attrID = attribs.getIDs();
    while (attrID.hasMore()) {
        def attribute = attrID.next()
        print attribute + ":"
        def value = ((BasicAttribute) attribs.get(attribute))
        if (value!=null) {
            NamingEnumeration values = value.getAll()
            while (values.hasMore()) {
                print (values.next().toString()+";")
            }
        }
        println "<br/>"
    }
    println "</td></tr>"
}
println "</table>"