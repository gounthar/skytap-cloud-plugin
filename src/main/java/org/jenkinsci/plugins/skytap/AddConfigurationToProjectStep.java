//
// Copyright (c) 2013, Skytap, Inc
// 
// Permission is hereby granted, free of charge, to any person obtaining a 
// copy of this software and associated documentation files (the "Software"), 
// to deal in the Software without restriction, including without limitation 
// the rights to use, copy, modify, merge, publish, distribute, sublicense, 
// and/or sell copies of the Software, and to permit persons to whom the 
// Software is furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in 
// all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN 
// THE SOFTWARE.
//
package org.jenkinsci.plugins.skytap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.jenkinsci.plugins.skytap.SkytapBuilder.SkytapAction;
import org.jenkinsci.plugins.skytap.SkytapBuilder.SkytapActionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

public class AddConfigurationToProjectStep extends SkytapAction {

	private final String configurationID;
	private final String configurationFile;

	private final String projectID;
	private final String projectName;

	// these vars will be initialized when the step is run

	@XStreamOmitField
	private SkytapGlobalVariables globalVars;

	// the runtime environment id will be set one of two ways:
	// either the user has provided just a environment id, so we use it,
	// or the user provided a file, in which case we read the file and extract
	// the
	// id from the json element
	@XStreamOmitField
	private String runtimeConfigurationID;
	
	@XStreamOmitField
	private String authCredentials;

	@DataBoundConstructor
	public AddConfigurationToProjectStep(String configurationID,
			String configurationFile, String projectID, String projectName) {
		super("Add Configuration to Project");
		this.configurationID = configurationID;
		this.configurationFile = configurationFile;
		this.projectID = projectID;
		this.projectName = projectName;
	}
	
	public Boolean executeStep(AbstractBuild build,
			SkytapGlobalVariables globalVars) {
		
		JenkinsLogger.defaultLogMessage("----------------------------------------");
		JenkinsLogger.defaultLogMessage("Adding Environment to Project Step");
		JenkinsLogger.defaultLogMessage("----------------------------------------");
				
		JenkinsLogger.defaultLogMessage(projectName);
		if(preFlightSanityChecks()==false){
			return false;
		}
		
		this.globalVars = globalVars;
		authCredentials = SkytapUtils.getAuthCredentials(build);
			
		// reset step parameters with env vars resolved at runtime
		String expConfigurationFile = SkytapUtils.expandEnvVars(build,
				configurationFile);
		
		// if user has provided just a filename with no path, default to
		// place it in their Jenkins workspace

		if (!expConfigurationFile.equals("")) {
			expConfigurationFile = SkytapUtils.convertFileNameToFullPath(build,
					expConfigurationFile);
		}
		
		// get runtime environment id
		try {
			runtimeConfigurationID = SkytapUtils.getRuntimeId(build, configurationID, expConfigurationFile);
		} catch (FileNotFoundException e) {
			JenkinsLogger.error("Error obtaining runtime id: " + e.getMessage());
			return false;
		}
		
		// get runtime project ID
		String runtimeProjectID = "";
		
		// was a project id provided? then just use it
		if(!getProjectID().equals("")){ 
			runtimeProjectID = getProjectID(); 
		}
			
		// otherwise retrieve the id from name provided
		if(!getProjectName().equals("")){
			runtimeProjectID = SkytapUtils.getProjectID(projectName, authCredentials);
		}
		
		if(runtimeProjectID.equals("")){
			JenkinsLogger.error("Please provide a valid project name or ID.");
			return false;
		}

		JenkinsLogger.log("Environment ID: " + runtimeConfigurationID);
		JenkinsLogger.log("Environment File: " + this.configurationFile);
		JenkinsLogger.log("Project ID: " + runtimeProjectID);
		JenkinsLogger.log("Project Name: " + this.projectName);

		// build request url
		String requestUrl = buildRequestURL(runtimeProjectID, runtimeConfigurationID);

		// create request for skytap API
		HttpPost hp = SkytapUtils.buildHttpPostRequest(requestUrl,authCredentials);

		// execute request
		String httpRespBody;
		try {
			httpRespBody = SkytapUtils.executeHttpRequest(hp);
		} catch (SkytapException e) {
			JenkinsLogger.error("Skytap Exception: " + e.getMessage());
			return false;
		}

		try {
			SkytapUtils.checkResponseForErrors(httpRespBody);
		} catch (SkytapException ex) {
			JenkinsLogger.error("Request returned an error: " + ex.getError());
			JenkinsLogger.error("Failing build step.");
			return false;
		}
		
		JenkinsLogger.log("");
		JenkinsLogger.log(httpRespBody);
		JenkinsLogger.log("");
		
		JenkinsLogger.defaultLogMessage("Environment " + runtimeConfigurationID + " was successfully added to project " + runtimeProjectID);
		JenkinsLogger.defaultLogMessage("----------------------------------------");
		
		return true;
	}

	
	private String buildRequestURL(String projId, String configId) {

		JenkinsLogger.log("Building request url ...");

		StringBuilder sb = new StringBuilder("https://cloud.skytap.com");
		
		sb.append("/projects/");
		sb.append(projId);
		sb.append("/configurations/");
		sb.append(configId);

		JenkinsLogger.log("Request URL: " + sb.toString());
		return sb.toString();

	}
	

	/**
	 * This method is a final check to ensure that user inputs are legitimate.
	 * Any situation where the user has entered both inputs in an either/or scenario 
	 * will fail the build. If the user has left both blank where we need one, it will
	 * also fail.
	 * 
	 * @return Boolean sanityCheckPassed
	 */
	private Boolean preFlightSanityChecks(){

		// check whether user entered both values for environment id/conf file
		if(!this.configurationID.equals("") && !this.configurationFile.equals("")){
			JenkinsLogger.error("Values were provided for both environment ID and file. Please provide just one or the other.");
			return false;
		}
		
		// check whether we have neither environment id or file
		if(this.configurationFile.equals("") && this.configurationID.equals("")){
			JenkinsLogger.error("No value was provided for environment ID or file. Please provide either a valid Skytap environment ID, or a valid environment file.");
			return false;
		}
		
		// check whether user entered both values for project id/project name
		if(!this.projectID.equals("") && !this.projectName.equals("")){
			JenkinsLogger.error("Values were provided for both project ID and file. Please provide just one or the other.");
			return false;
		}
		
		// check whether we have neither project id nor name
		if(this.projectID.equals("") && this.projectName.equals("")){
			JenkinsLogger.error("No value was provided for project ID or name. Please provide either the name or ID of a valid Skytap project.");
			return false;
		}
		
		return true;
	}

	@Extension
	public static final SkytapActionDescriptor D = new SkytapActionDescriptor(
			AddConfigurationToProjectStep.class, "Add Environment to Project");
	
	public String getConfigurationID() {
		return configurationID;
	}

	public String getConfigurationFile() {
		return configurationFile;
	}

	public String getProjectID() {
		return projectID;
	}

	public String getProjectName() {
		return projectName;
	}

}
