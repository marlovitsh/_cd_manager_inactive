package ch.marlovits.cdManager;

import org.eclipse.core.runtime.jobs.Job;

public abstract class JobWithErrorCode extends Job {
	protected int errorCode_;
	
	public JobWithErrorCode(String name){
		super(name);
		errorCode_ = 0;
	}
	
	/**
	 * returns the error code
	 * 
	 * @return error code - an integer:
	 */
	public int getErrorCode(){
		return errorCode_;
	}
	
	/**
	 * set the error code
	 */
	public void setErrorCode(int errorCode){
		errorCode_ = errorCode;
	}
}
