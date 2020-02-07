package io.dbmaster.tools

// TODO - move DbMaster class to API
import com.branegy.scripting.DbMaster
import com.branegy.dbmaster.util.NameMap
import org.slf4j.Logger

import javax.naming.*
import javax.naming.directory.*
import javax.naming.ldap.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

public class LdapUserCache { 
    
    private DbMaster dbm
    private Logger logger
    
    public NameMap ldapAccountByDN   = new NameMap()
    public NameMap ldapAccountByName = new NameMap()
    public NameMap ldapAccountBySid  = new NameMap()

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
            def ldapAttributes = "member;memberOf;sAMAccountName;distinguishedName;name;userAccountControl;objectSid"
            logger.info("Retrieving ldap accounts and groups")
            
            String ldapContext = null
            String domain = null
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

            if (ldapContext==null) {
                logger.warn("Define property 'defaultContext' for connection '${connection.name}'")
            }
            if (domain==null) {
                logger.warn("Define property 'domain' for connection '${connection.name}'")
            }
            
            if (ldapContext!=null && domain!=null) {
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
                    def userAccountControl = attributes.get("userAccountControl")?.get()
                    def title = attributes.get("name")?.get();
                    def sid = attributes.get("objectSid")?.get();
                    String sidStr = convertSidToStr( sid )
                    String sidHex = bytesToHex(sid)

                    // logger.warn("name="+ name + " sid "+ sidStr + " hex="+ sidHex +" class " + sid.getClass().getName());

                    def account = [ "name" : name, "dn" : dn, "members" : members, 
                                    "member_of" : member_of, "title": title, 
                                    "accountControl" : userAccountControl, "domain": domain, "sidStr" : sidStr, "sidHex" : sidHex]

                    ldapAccountByDN[dn] = account
                    ldapAccountByName[domain+"\\"+name] = account
                    ldapAccountBySid[sidHex] = account
                }
            }
        }
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


    /*
     * The binary data structure, from http://msdn.microsoft.com/en-us/library/cc230371(PROT.10).aspx:
     *   byte[0] - Revision (1 byte): An 8-bit unsigned integer that specifies the revision level of the SID structure. This value MUST be set to 0x01.
     *   byte[1] - SubAuthorityCount (1 byte): An 8-bit unsigned integer that specifies the number of elements in the SubAuthority array. The maximum number of elements allowed is 15.
     *   byte[2-7] - IdentifierAuthority (6 bytes): A SID_IDENTIFIER_AUTHORITY structure that contains information, which indicates the authority under which the SID was created. It describes the entity that created the SID and manages the account.
     *               Six element arrays of 8-bit unsigned integers that specify the top-level authority 
     *               big-endian!
     *   and then - SubAuthority (variable): A variable length array of unsigned 32-bit integers that uniquely identifies a principal relative to the IdentifierAuthority. Its length is determined by SubAuthorityCount. 
     *              little-endian!
     */

	public static String convertSidToStr(byte[] sid) {
		if (sid==null) return null;
		if (sid.length<8 || sid.length % 4 != 0) return "";
		StringBuilder sb = new StringBuilder();
		sb.append("S-").append(sid[0]);
		int c = sid[1]; // Init with Subauthority Count.
		ByteBuffer bb = ByteBuffer.wrap(sid);
		sb.append("-").append((long)bb.getLong() & 0XFFFFFFFFFFFFL);
		bb.order(ByteOrder.LITTLE_ENDIAN); // Now switch.
		for (int i=0; i<c; i++) { // Create Subauthorities.
			sb.append("-").append((long)bb.getInt() & 0xFFFFFFFFL);
		}        
		return sb.toString();    
	}

	// https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return (new String(hexChars));
	}
}