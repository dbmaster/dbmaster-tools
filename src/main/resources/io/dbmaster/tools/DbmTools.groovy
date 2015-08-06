package io.dbmaster.tools

import groovy.sql.Sql

import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.connection.ConnectionProvider
import com.branegy.service.connection.api.ConnectionService
import com.branegy.service.base.api.ProjectService

import org.slf4j.Logger
import com.branegy.scripting.DbMaster

import java.sql.ResultSet
import java.sql.Statement
import java.sql.ResultSetMetaData
import java.sql.Connection
import java.util.*
import java.io.PrintWriter
import org.apache.commons.io.IOUtils


public class DbmTools {

    public DbMaster dbm
    public Logger logger
    protected PrintWriter out
    protected String projectName
    
    DbmTools(dbm, logger, out) {
        this.dbm    = dbm
        this.logger = logger
        this.out    = out
        projectName =  dbm.getService(ProjectService.class).getCurrentProject().getName()
    }
    
    public String getCurrentUser() {
        return com.branegy.persistence.CurrentUserService.getCurrentUser()
    }
    
    public String getCurrentProject() {
        return projectName
    }
    
    public getConnection(String dbServer, String dbName = null) {
        logger.info("Connecting to ${dbServer} ${dbName?:""}")
        def connectionSrv = dbm.getService(ConnectionService.class)
        
        def connectionInfo = connectionSrv.findByName(dbServer)
        def connection = ConnectionProvider.getConnector(connectionInfo).getJdbcConnection(dbName)
        dbm.closeResourceOnExit(connection)
        return connection
    }
    
    public connect (String databaseName) {
        logger.info("Connecting to ${databaseName}")
        def connectionSrv = dbm.getService(ConnectionService.class)
        
        String[] parts = databaseName.split("\\.")

        def serverName = parts.length>=3 ? parts[0]+parts[1] : parts[0];
        def dbName     = parts[parts.length-1];
        def connectionInfo = connectionSrv.findByName(serverName)
        def connection = ConnectionProvider.getConnector(connectionInfo).getJdbcConnection(dbName)
        dbm.closeResourceOnExit(connection)
        return connection
    }
    
    public quote(term) {
    
    }

    public Object convertObject(Object data) {
        if (data == null) {
            return null
        } else if (data instanceof java.sql.Clob) {
            Reader reader = data.getCharacterStream();
            return IOUtils.toString(reader);
        } else {
            return data
        }
    }

    /*
     *  printHeader = false is usefull when running multi-server queries
     */
    public int printResultSet (rs, printHeader = true) {
        ResultSetMetaData metadata = rs.getMetaData();
        int columnCount = metadata.getColumnCount();

        if (printHeader) {
            out.println """<table cellspacing="0" class="simple-table" border="1">
                           <tr style=\"background-color:#EEE\">"""

            for (int i=1; i<=columnCount; ++i){
               out.println "<th>${metadata.getColumnName(i)}</th>"
           }      
           out.println "</tr>"
        }

        int rows = 0
        Object cell
        while (rs.next()) {
            out.print "<tr>"
            for (int i=1; i<=columnCount; ++i) {
                cell = convertObject(rs.getObject(i))
                if (cell instanceof Number) {
                    out.print "<td align=\"right\">"
                } else {
                    out.print "<td>"
                }
                out.print cell==null ? "NULL" : cell.toString()
                out.print "</td>"
            }
            out.println "</tr>"
            rows = rows + 1
        }
        if (printHeader) {
            out.println "</table>"
        }
        return rows
    }

    public int printUpdateCount (int updated) {
        out.println "${updated} row(s) affected.<br/>"
        return updated
    }

    public Object execute(Connection connection, String query, Map params = null) {
        print(connection, query, params, true)
    }

    /*
     *  printHeader = false is usefull when running multi-server queries
     */
    public Object print(Connection connection, String query, Map params = null, boolean printHeader = true) {
        def sql = new groovy.sql.Sql ( connection )
        def newParams = Collections.singletonList(params ?: [:])
        
        groovy.sql.SqlWithParams updated = sql.checkForNamedParams(query, newParams)
        java.sql.PreparedStatement ps = connection.prepareStatement(updated.getSql())
        int i = 1
        logger.debug("Running query: ${updated.getSql()}")
        if (params !=null) {
            for (Object value : updated.getParams()) {
                logger.debug("Set parameter ${i} ${value}")
                ps.setObject(i++, value)
            }
        }
        boolean isResultSet = ps.execute()
        int updateCount = ps.getUpdateCount()
        def queryResult = []
        while(isResultSet || updateCount != -1) {
            if (isResultSet) {
                ResultSet resultSet = ps.getResultSet()
                queryResult << printResultSet(resultSet, printHeader)
            } else {
                queryResult << printUpdateCount(updateCount)
            }
            isResultSet = ps.getMoreResults()
            updateCount = ps.getUpdateCount()
        }
        return queryResult
    }
    
    public String toURL (String link) {
        return link==null ? "NULL" : java.net.URLEncoder.encode(link).replaceAll("\\+", "%20")
    }
    
    public String linkToObject(String type, String serverName, String objectName) {
        def prefix = "#inventory/project:${toURL(projectName)}"
        if (type.equals("Application")) {
            return "${prefix}/applications/application:${toURL(objectName)}"
        } else if (type.equals("Server")) {
            return  "${prefix}/servers/server:${toURL(serverName)}"
        } else if (type.equals("Database")) {
            return  "${prefix}/databases/connection:${toURL(serverName)},db:${toURL(objectName)}"
        } else if (type.equals("Connection")) {
            return  "${prefix}/connections/connection:${toURL(serverName)}" 
        } else if (type.equals("Job")) {
            // TODO we do not have any information about jobs in dbmaster yet
            return  "${prefix}/databases" 
        } else {
            throw new RuntimeException("Object type ${type} was not expected")
        }
    }
}