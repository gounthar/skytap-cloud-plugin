package org.jenkinsci.plugins.skytap;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;

public class SkytapBuilder extends Builder {

    private final SkytapAction action;

    @DataBoundConstructor
    public SkytapBuilder(SkytapAction action) {
        this.action = action;
    }

    public SkytapAction getAction(){
    	return action;
    }

    public static class SkytapAction implements ExtensionPoint, Describable<SkytapAction> {
        public String displayName;
        
        public SkytapAction(String displayName) {
            this.displayName = displayName;
        }
        
		// this method will be called in the 'perform' method of the builder
        // every step class knows how to perform its build step
    	public Boolean executeStep(AbstractBuild build, SkytapGlobalVariables globalVars){
    		return true;
    	}
    	
        public String getDisplayName() {
			return displayName;
		}
    	
		public Descriptor<SkytapAction> getDescriptor() {
            return Hudson.getInstance().getDescriptor(getClass());
        }
    }
    
    public static class SkytapActionDescriptor extends Descriptor<SkytapAction> {
        
    	// this is the name we want to appear in the drop down
    	private String displayName;
    	
    	public SkytapActionDescriptor(Class<? extends SkytapAction> clazz, String displayName) {
            super(clazz);            
            this.displayName = displayName;
            
        }
        public String getDisplayName() {
            return displayName;
        }
        
        public FormValidation doCheckConfigurationID(@QueryParameter String configurationID, @QueryParameter String configurationFile) throws IOException, ServletException {
        	
        	// make sure the id is a valid number
        	try {
        		if(!configurationID.equals("")){Integer.parseInt(configurationID);}
        	  } catch (NumberFormatException e) {
        	    return FormValidation.error("Please enter a valid integer for the configuration ID.");
        	  }
        	
        	// if user has entered both values, slap them on the wrist
        	if(!configurationID.equals("") && !configurationFile.equals("")){
        		return FormValidation.error("Please enter either a valid configuration ID or a valid configuration file. Build step will fail if both values are entered.");
        	}
        	
    	    return FormValidation.ok();
        	}
                
        public FormValidation doCheckProjectID(@QueryParameter String projectID, @QueryParameter String projectName ){
        	
        	// make sure id is a valid number
        	try {
        		if(!projectID.equals("")){Integer.parseInt(projectID);}
        	  } catch (NumberFormatException e) {
        	    return FormValidation.error("Please enter a valid integer for the project ID.");
        	  }
        	
        	// if user has entered both values, slap them on the wrist
        	if(!projectID.equals("") && !projectName.equals("")){
          		return FormValidation.error("Please enter either a valid project ID or a valid project name. Build step will fail if both values are entered.");
        	}
        	
        	return FormValidation.ok();
        }
        
    }
    
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
            	
		// initialize global vars object to be passed to the skytap action
		SkytapGlobalVariables globalVars = new SkytapGlobalVariables(getDescriptor().isLoggingEnabled());
		
		// instantiate a Jenkins Logger for use by the steps
		JenkinsLogger theLogger = new JenkinsLogger(listener, getDescriptor().isLoggingEnabled());
		
    	Boolean stepSucceeded = action.executeStep(build, globalVars);
    	return stepSucceeded;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }
    


    /**
     * Descriptor for {@link SkytapBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/SkytapBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		private Boolean loggingEnabled = true;

		public DescriptorImpl() {
			load();
		}

		// Indicates that this builder can be used with all kinds of project types 
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Execute Skytap Action";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

        	loggingEnabled = formData.getBoolean("loggingEnabled");
        	
            save();
            return super.configure(req,formData);
        }
        
		public Boolean isLoggingEnabled() {
			return loggingEnabled;
		}

        
    }
}

