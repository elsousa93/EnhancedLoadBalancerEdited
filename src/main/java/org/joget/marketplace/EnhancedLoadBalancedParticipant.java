//added a comment for testing purposes
package org.joget.marketplace;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.User;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.workflow.model.DefaultParticipantPlugin;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.joget.directory.model.Group;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.joget.apps.app.service.AppPluginUtil;
import java.sql.*;



public class EnhancedLoadBalancedParticipant extends DefaultParticipantPlugin {
    
    private final static String MESSAGE_PATH = "messages/enhancedLoadBalancedParticipant";
    


    public Collection<String> getActivityAssignments(Map properties) {
	//Initialize
	String assignTo = "";
        int lowestAssignmentCount = -1;
        int userAssignmentSize;
        boolean ranOnce = false;
        String packageId = null;
        Collection assignees = new ArrayList();
        ExtDirectoryManager directoryManager = (ExtDirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        WorkflowActivity workflowActivity = (WorkflowActivity) properties.get("workflowActivity");
        User currentUser = directoryManager.getUserByUsername(workflowUserManager.getCurrentUsername());
        
        //LS - New variables created
        Connection con = null;
        String sql =  null;
        String userListString = "";
        Collection<User> userListToRemove = new ArrayList<User>();
        Collection<User> CoordenadoresToRemove = new ArrayList<User>();
        String connectionString = null;

        //Get current user's role
        String currentThreadUser = workflowUserManager.getCurrentThreadUser();

        //Get user defined plugin configurations
        String orgId = (String) properties.get("orgId");
        String deptId = (String) properties.get("deptId");
        String deptIdDesc = (String) properties.get("deptIdDesc");
	String groupId = (String) properties.get("groupId");
	String noChooseCurrentUser = (String) properties.get("getChoiceCurrentUser");
        String considerAppOnly = (String) properties.get("getChoiceThisApp");
        String noChooseActivity = (String) properties.get("exclusion");
        String debugMode = (String) properties.get("debugMode");
        
        //LS - Get user defined plugin configurations
        String databaseType = (String) properties.get("getChoiceDatabaseType");
        String databaseAddress = (String) properties.get("getChoiceDatabaseAddress");
        String databasePort = (String) properties.get("getChoiceDatabasePort");
        String databaseUser = (String) properties.get("getChoiceDatabaseUser");
        String databasePass = (String) properties.get("getChoiceDatabasePass");
        String LastLanePerformer = (String) properties.get("getChoiceLastLanePerformer");
        String assingToLastUser = (String) properties.get("getChoiceAssignToLastPerformer");
        boolean assignToLastLaneUser = false;
        
        //Set variables
        //Assign to Last user option
        if(assingToLastUser == null || "".equalsIgnoreCase(assingToLastUser)){
            assingToLastUser = "false";
        }
        
        if(deptId == null || "".equalsIgnoreCase(deptId)){
            deptId = deptIdDesc;
        }
        
        sql = "SELECT COUNT(*) as flag_ferias from app_fd_lista_ferias where STR_TO_DATE(c_data_inicio_ferias, \"%d-%m-%Y\") <= Date(CURDATE()) and STR_TO_DATE(c_data_fim_ferias, \"%d-%m-%Y\") >= Date(CURDATE()) and c_nome_utilizador = ? and c_estado = 'aprovado'"; 
        
        if("true".equalsIgnoreCase(debugMode)){
            LogUtil.info("Plug In - Load Banlancer Partcipant", "Last Lane User: " + assingToLastUser);
            LogUtil.info("Plug In", "deptId: " + deptId);
        }
        
        //Configure connection string acording to the configuration information
        if(databaseType.equals("mySql")){
            connectionString = "jdbc:mysql://"+ databaseAddress +":"+ databasePort +"/jwdb?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
            LogUtil.info("connectionString", "connectionString" + connectionString);
        }else{
            LogUtil.info("Application", "Database Not compatible with the plugin");
        }
        
        //Config params for getUsers is sensitive to blank string VS NULL value
        if ("".equals(orgId)) {
            orgId = null;
        }
        if ("".equals(deptId)) {
            deptId = null;
        }
        if ("".equals(groupId)) {
            groupId = null;
        }

        
        //LS - Assign to the previous user of the lane
        //Condition to check if the user wants to assign to previous user or not
        if("true".equalsIgnoreCase(assingToLastUser)){
            //Condition to check if there is already a previous performer fot this specific lane
            if(LastLanePerformer != null && !LastLanePerformer.equals("")){
                
                //connect to database
                try{
                    Class.forName("com.mysql.jdbc.Driver").newInstance();
                    con = DriverManager.getConnection(connectionString, databaseUser, databasePass);
                }catch(Exception e){
                    LogUtil.error("Application",e, "Error connecting to database");
                }
                
                //Check if the last performer is available to recieve task
                try{
                    PreparedStatement stmt = con.prepareStatement(sql);
                    stmt.setString(1, LastLanePerformer);
                    ResultSet rs = stmt.executeQuery();
                    
                    //Add to a list to remove the rusers thar are absent
                    while (rs.next()) {
                        if( rs.getString(1).equals("0") == false){
                            assignToLastLaneUser = false;
                        }else{
                            assignToLastLaneUser = true;
                        }
                    }
                }catch(Exception runningQuery){
                    LogUtil.error("Application",runningQuery, "Error executing query: " + sql);
                }
                
                //LS - Closing database
                if (con !=null) {
                    try{
                       con.close(); 
                    }catch(Exception close){
                        LogUtil.error("Application",close, "Error closing connection to database");
                    }
            
                }

                if(assignToLastLaneUser == true){
                    LogUtil.info("BeanShell Assign to", "Assign to previous lane user: " + LastLanePerformer);
                    assignTo = LastLanePerformer;
                    assignees.add(assignTo);
                    return assignees;
                }
            }
        }

        
        
        //Get users of the selection as a list, sort by first name
        Collection<User> userList = directoryManager.getUsers(null, orgId, deptId, null, groupId, null, "1", "firstName", false, null, null);
        
        //Remove "Coordenadores"
        boolean inGroup = false;
        String groupUserId = "Coordenador";
        for (Object u : userList) {
            User user = (User) u;
            Collection<Group> groups = directoryManager.getGroupByUsername(user.getUsername());
            if (groups != null) {
                inGroup = false;
                for (Group g : groups) {
                    if (groupUserId.equals(g.getId())) {
                        if("true".equalsIgnoreCase(debugMode)){
                            LogUtil.info(this.getClass().getName(), "Remover Coordenador - Username: " + user.getUsername());
                        }
                        inGroup = true;
                    }
                }
                if (inGroup == true){
                    CoordenadoresToRemove.add(user);
                }
            }
        }
        userList.removeAll(CoordenadoresToRemove);
        
        
        if("true".equalsIgnoreCase(debugMode)){
            for (Object u : userList) {
                    User user = (User) u;
                    userListString += "'" + user.getUsername() + "'" + ", ";
            }
        }
        
        //LS - After getting the list with all the users it is necessary to eliminate the ones that are absent:
        if (userList.size() > 0){
         
            //LS - Open DB
            try{
                Class.forName("com.mysql.jdbc.Driver").newInstance();
                //con = DriverManager.getConnection("jdbc:mysql://localhost:3307/jwdb?characterEncoding=UTF-8", "root", "");
                LogUtil.info("connectionString", "databaseUser: " + databaseUser + "databasePass: " + databasePass);
                con = DriverManager.getConnection(connectionString, databaseUser, databasePass);
            }catch(Exception e){
                LogUtil.error("Application",e, "Error connecting to database");
            }

            //LS - Querie to get if a certain user is absent
            try{

                for(Object u : userList){
                    User user = (User) u;
                    
                    PreparedStatement stmt = con.prepareStatement(sql);
                    stmt.setString(1, user.getUsername());
                    ResultSet rs = stmt.executeQuery();
                    
                    //Add to a list to remove the rusers thar are absent
                    while (rs.next()) {
                        if( rs.getString(1).equals("0") == false){
                            userListToRemove.add(user);
                        }
                    }
                }
                //Remove the absent users from the original list
                userList.removeAll(userListToRemove);

            }catch(Exception runningQuery){
                LogUtil.error("Application",runningQuery, "Error executing query: " + sql);
            }


            //LS - Closing database
            if (con !=null) {
                try{
                   con.close(); 
                }catch(Exception close){
                    LogUtil.error("Application",close, "Error closing connection to database");
                }
            
            };
            
        }     
        
        
        //DEFAULT FALLBACK: If selection is empty before OR after filtering, return null as so to let system decide who to assign
        //This gives precedence to rules of "exclude current logged in user" & "Exclude involved users from selected activities"
        if (userList == null || userList.isEmpty()) {
            if("true".equalsIgnoreCase(debugMode)){
                LogUtil.info(this.getClass().getName(), "No user assigned because no user found");
            }
            return null;
        } else {
            //Get this user's id to remove from userList, IF option is TRUE, IF list have current user
            if ("true".equalsIgnoreCase(noChooseCurrentUser) && userList.contains(currentUser)) {
                userList.remove(currentUser);
                
                if("true".equalsIgnoreCase(debugMode)){
                    LogUtil.info(this.getClass().getName(), "Not considering current logged in user - " + currentUser.getUsername());
                }
            }

            //Get app id to limit assignment count to current app only, IF option is TRUE
            if ("true".equalsIgnoreCase(considerAppOnly)) {
                packageId = AppUtil.getCurrentAppDefinition().getAppId();
                
                if("true".equalsIgnoreCase(debugMode)){
                    LogUtil.info(this.getClass().getName(), "Checking current App assignments only - " + packageId);
                }
            }

            //Ignore consideration for users previously involved in selected activity(s)
            Collection<WorkflowActivity> activityList = workflowManager.getActivityList(workflowActivity.getProcessId(), null, null, null, null);
            
            if("true".equalsIgnoreCase(debugMode)){
                LogUtil.info(this.getClass().getName(), "Not considering activity - " + noChooseActivity);
            }
            
            for (WorkflowActivity currentActivity : activityList) {
                WorkflowActivity currentActivityInfo = workflowManager.getRunningActivityInfo(currentActivity.getId());

                if (currentActivityInfo != null && excluded(noChooseActivity, currentActivity)) {
                    User performer = directoryManager.getUserByUsername(currentActivityInfo.getNameOfAcceptedUser());

                    if (userList.contains(performer)) {
                        if("true".equalsIgnoreCase(debugMode)){
                            LogUtil.info(this.getClass().getName(), "Not considering activity " + currentActivity.getName() + " performed by " + performer.getUsername());
                        }
                        userList.remove(performer);
                    }
                }
            }

            //userList may be empty by now
            //Loop through all users in specified group
            try {
                for (Object u : userList) {
                    User user = (User) u;
                    workflowUserManager.setCurrentThreadUser(user.getUsername());

                    //get open assignment count of the current user
                    if(deptId.contains("_CNT_")){
                        userAssignmentSize = 0;
                        sql = "SELECT count(sp.Id) as ProcessosAtivos FROM shkprocesses sp JOIN app_fd_processos p on sp.ActivityRequesterProcessId = p.id LEFT JOIN shkprocessstates sps ON sps.oid = sp.State LEFT JOIN SHKActivities sact ON sact.ProcessId = sp.Id LEFT JOIN SHKActivityStates ssta ON ssta.oid = sact.State LEFT JOIN SHKAssignmentsTable sass ON sact.Id = sass.ActivityId LEFT JOIN app_fd_detalhe_processo dp ON dp.id = p.c_detalhe_processo WHERE sps.KeyValue LIKE 'open.running' AND (ssta.KeyValue LIKE 'open.not_running.not_started' OR ssta.KeyValue LIKE 'open.running') AND (c_estado_contratacao is null or (c_estado_contratacao <> 'PND' AND c_estado_contratacao <> 'PNDEQ')) AND sass.resourceid LIKE ?"; 
                        
                        //LS - Open DB
                        try{
                            Class.forName("com.mysql.jdbc.Driver").newInstance();
                            //con = DriverManager.getConnection("jdbc:mysql://localhost:3307/jwdb?characterEncoding=UTF-8", "root", "");
                            con = DriverManager.getConnection(connectionString, databaseUser, databasePass);
                        }catch(Exception e){
                            LogUtil.error("Application",e, "Error connecting to database");
                        }
                    
                        //LS - Querie to get if a certain user is absent
                        try{


                            PreparedStatement stmt = con.prepareStatement(sql);
                            stmt.setString(1, user.getUsername());
                            ResultSet rs = stmt.executeQuery();

                            //Add to a list to remove the rusers thar are absent
                            while (rs.next()) {
                                userAssignmentSize = Integer.parseInt(rs.getString(1));
                            }

                        }catch(Exception runningQuery){
                            LogUtil.error("Application",runningQuery, "Error executing query: " + sql);
                        }
                        LogUtil.info(this.getClass().getName(), "Assign to user CNT");
                        LogUtil.info(this.getClass().getName(), "Processos ativos: " + userAssignmentSize);
                        
                                    //LS - Closing database
                        if (con !=null) {
                            try{
                               con.close(); 
                            }catch(Exception close){
                                LogUtil.error("Application",close, "Error closing connection to database");
                            }

                        };
                    }else{
                        userAssignmentSize = workflowManager.getAssignmentSize(packageId, null, null);
                        LogUtil.info(this.getClass().getName(), "Assign to user not from CNT");
                        LogUtil.info(this.getClass().getName(), "Processos totais: " + userAssignmentSize);
                    }
                    

                    //Run this only once
                    //Assign first person's value first to have a value to begin comparison
                    //Assign first just in case first is already lowest
                    if (!ranOnce) {
                        lowestAssignmentCount = userAssignmentSize;
                        assignTo = user.getUsername();
                        ranOnce = true;
                    }

                    //Start comparison here
                    if (userAssignmentSize < lowestAssignmentCount) {
                        assignTo = user.getUsername();
                        lowestAssignmentCount = userAssignmentSize;
                    }
                    
                    if("true".equalsIgnoreCase(debugMode)){
                        LogUtil.info(this.getClass().getName(), user.getUsername() + " - " + userAssignmentSize + " assignment");
                    }
                }
            } catch (Exception e) {
                LogUtil.error(this.getClass().getName(), e, "Failed to check all users in loop");
            } finally {
                //Set back original user role
                workflowUserManager.setCurrentThreadUser(currentThreadUser);
                
                if(userList.isEmpty()){
                    LogUtil.debug(this.getClass().getName(), "No user found to check");
                }
            }

            if (assignTo == null || "".equals(assignTo)) {
                if("true".equalsIgnoreCase(debugMode)){
                    LogUtil.info(this.getClass().getName(), "No user assigned");
                }
                return null;
            } else {
                if("true".equalsIgnoreCase(debugMode)){
                    LogUtil.info(this.getClass().getName(), "Assign to " + assignTo);
                }
                assignees.add(assignTo);
                return assignees;
            }
        }
    }

    //Method to check which activity(s) is selected to ignore involved user
    private boolean excluded(String exclusion, WorkflowActivity activity) {

        Collection<String> exclusionIds = new ArrayList<String>();

        if (exclusion != null && !"".equals(exclusion)) {
            exclusionIds.addAll(Arrays.asList(exclusion.split(";")));
        }

        return exclusionIds.contains(WorkflowUtil.getProcessDefIdWithoutVersion(activity.getProcessDefId()) + "-" + activity.getActivityDefId());
    }

    //Return a UNIQUE name for the plugin
    //WARNING: If same plugin name already exists, THIS plugin WILL override 'old' plugin
    public String getName() {
        return "Enhanced Load Balanced Participant";
    }

    //Return plugin label
    public String getLabel() {
        return AppPluginUtil.getMessage("form.enhancedLoadBalancedParticipant.pluginLabel", getClassName(), MESSAGE_PATH);

    }

    //Return plugin description
    public String getDescription() {
        return AppPluginUtil.getMessage("form.enhancedLoadBalancedParticipant.pluginDesc", getClassName(), MESSAGE_PATH);
    }

	//Return class name of plugin
	public String getClassName() {
		return getClass().getName();
	}

	//Return plugin property options as a JSON
    //This defines the UI for customizing plugin functions
    //OPTIONAL
	public String getPropertyOptions() {
		return AppUtil.readPluginResource(getClass().getName(),
                "/properties/EnhancedLoadBalancedParticipant.json", null, true,MESSAGE_PATH);
	}

	//Return the plugin version
	public String getVersion() {
		return "7.0.0";
	}
}