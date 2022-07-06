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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.joget.apps.app.service.AppPluginUtil;

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

        //Get current user's role
        String currentThreadUser = workflowUserManager.getCurrentThreadUser();

        //Get user defined plugin configurations
        String orgId = (String) properties.get("orgId");
        String deptId = (String) properties.get("deptId");
	String groupId = (String) properties.get("groupId");
	String noChooseCurrentUser = (String) properties.get("getChoiceCurrentUser");
        String considerAppOnly = (String) properties.get("getChoiceThisApp");
        String noChooseActivity = (String) properties.get("exclusion");
        String debugMode = (String) properties.get("debugMode");

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

        //Get users of the selection as a list, sort by first name
        Collection<User> userList = directoryManager.getUsers(null, orgId, deptId, null, groupId, null, null, "firstName", false, null, null);
        if("true".equalsIgnoreCase(debugMode)){
            String userListString = "";
            for (Object u : userList) {
                    User user = (User) u;
                    userListString += user.getUsername() + ", ";
            }
            LogUtil.info(this.getClass().getName(), "Userlist - " + userList.size() + " - " + userListString);
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
                    userAssignmentSize = workflowManager.getAssignmentSize(packageId, null, null);

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