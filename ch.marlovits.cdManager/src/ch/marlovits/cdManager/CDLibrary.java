package ch.marlovits.cdManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.filechooser.FileSystemView;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * a class for reading and writing from/to CD's/DVD's, etc. For now based on cdrtools for windows.
 * 
 * @author marlovitsh
 * 
 */
public class CDLibrary {
	public static final int ERR_NOERR = 0;
	public static final int ERR_FILEALREADYTHERE = 1;
	public static final int ERR_INTERRUPTEDEXCEPTION = 2;
	public static final int ERR_IOEXCEPTION = 3;
	public static final int ERR_COPYNOTSUCCESSFUL = 4;
	public static final int ERR_EJECT = 5;
	
	static int currCDCopyCounter = 0;
	static int currCDCopyMax = 0;
	
	// ********** language...
	final public static String COPY_CD_TO_ISO_JOBNAME = "Kopiere Medium in Datei/iso-Abbild";
	final public static String EJECT_MEDIA_JOBNAME = "Medium auswerfen";
	final public static String COPY_CD_PROGRESSSTRING = "%s/%s Blöcken kopiert   %s%%";
	
	/**
	 * just for quick testing - disable in working copy
	 * 
	 * @param args
	 */
	/**
	 * this is the path to the commands for the cdrtools
	 */
	public static String commandBase =
		"F:\\Program Files\\cdrtools\\";
	
	// *** internally used: the last line of stderr to be examined for the "real result"
	public static String lastStdErrLine = "";
	public static String lastStdOutLine = "";
	
	// ***
	public static String capacityReadCDMatcher = "Capacity: ([0-9]+) Blocks";
	public static String progressReadCDMatcher = "addr:[ ]+([0-9]+) cnt: [0-9]+";
	// *** this must be present in stderr last line if successful
	public static String successfulReadCdMatcher =
		"Read [0-9]+.[0-9][0-9] kB at [0-9]+.[0-9] kB/sec.";
	// ***
	public static String CDRomMatcher =
		"[\r\n]+[\t]([0-9],[0-9],[0-9])[\t]([^\r^\n]+) Removable CD-ROM([\r\n]+)";
	
	// public static String CDRomMatcher =
	// "[\r\n]+[\t]([0-9],[0-9],[0-9])[\t]([^\r^\n]+) Removable Disk([\r\n]+)";
	
	public static String ejectErrorMatcher = "cdrecord: Invalid argument.";
	
	/**
	 * find all cd and dvd recorders. return a list of all available recorders
	 * 
	 * @return String[] list of all available devices in device-notation (e.g. "0,1,0")
	 */
	static public String[] findRecorders(){
// FileSystem fs = FileSystems.getDefault();
// String devicesString = "";
// String delim = "";
// for (Path rootPath : fs.getRootDirectories()) {
// try {
// FileStore store = Files.getFileStore(rootPath);
// if (store.type().toUpperCase().contains("CDFS")) {
// devicesString = devicesString + delim + rootPath + ": " + store.type();
// }
// delim = "\r";
// } catch (IOException e) {}
// }
// return devicesString.split("\r");
		
		// find in stdout the following lines:
		// 0,0,0 0) 'HL-DT-ST' 'DVDRAM GH22NS50 ' 'TN02' Removable CD-ROM
		// the point is to find lines ending with "Removable CD-ROM"
		// the dev-string is then at the start of the line, preceeded and followed by a single tab
		try {
			// the result is in stdout and must be analyzed - on error stdout is empty
			String[] command = {
				"cmd",
			};
			Process p = Runtime.getRuntime().exec(command);
			SyncPipe outPipe = new SyncPipe(p.getInputStream(), System.out, false);
			new Thread(outPipe).start();
			PrintWriter stdin = new PrintWriter(p.getOutputStream());
			// *** create the command
			stdin.println(commandBase + "cdrecord --scanbus");
			stdin.close();
			int returnCode = p.waitFor();
			
			// try to find <Removable CD-ROM> pattern in the output
			String theOutput = outPipe.getOutput();
			Pattern resultMatcher = Pattern.compile(CDRomMatcher);
			Matcher matcher = resultMatcher.matcher(theOutput);
			String devicesString = "";
			String delim = "";
			while (matcher.find()) {
				String dev = matcher.group(1);
				devicesString = devicesString + delim + dev;
				delim = "\r";
			}
			String[] devicesArray = devicesString.split("\r");
			System.out.println(devicesString);
			return devicesArray;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * get the currently mounted CDs/DVDs, returns the Name of the CDs/DVDs
	 * 
	 * @return
	 */
	static public String[] getMountedCDs(){
		String result = "";
		String delim = "";
		for (FileStore store : FileSystems.getDefault().getFileStores()) {
			if (store.type().toUpperCase().equalsIgnoreCase("CDFS")) {
				result = result + delim + store.name();
				delim = "\r";
			}
		}
		if (result.isEmpty())
			return null;
		else
			return result.split("\r");
	}
	
	/**
	 * Copy a whole CD/DVD to an iso file. If no device is specified, then the routine tries to find
	 * a device. If only one device is present, then this one is used - if then more than one device
	 * is present, the routine returns an error. Since this is a really time consuming process we
	 * put it into a job so that the main program can go on working.<br>
	 * If you have to test if the job is done/successfully done you can analyze the returned job,
	 * eg:<br>
	 * <ul>
	 * <code>
	 * Job myJob = copyCDToIso(jobName, deviceString, myIsoFilePath, true);<br>
	 *    if (myJob.getState() == Job.NONE )	{<br><ul>
	 * 	// job is done - now test if the job is SUCCESSFULLY done:<br>
	 * if (myJob.getErrorCode() != CDLibrary.ERR_NOERR)<br><ul>
	 * // some errors occured
	 * </ul>
	 * else
	 * <ul>
	 * // no error - successful!
	 * </ul>
	 * } </ul> </code> </ul> If you have to test if the job is SUCCESSFULLY done you can analyze the
	 * error code, eg:<br>
	 * 
	 * @param jobName
	 *            the name for the job which is shown in the progress indicator
	 * @param device
	 *            the device to be used - something like 0,1,0 or 0,0,0
	 * @param destinationFile
	 *            the destination iso path/file
	 * @param overwrite
	 *            should the destination file be overwritten if already present
	 * @return the job created by the routine. Needed if you have to test if the job is done.
	 */
	
	static public JobWithErrorCode copyCDToIso(String jobName, final String device,
		final String destinationFile, final boolean overwrite){
		JobWithErrorCode job1 = new JobWithErrorCode(COPY_CD_TO_ISO_JOBNAME) {
			@Override
			protected IStatus run(IProgressMonitor monitor){
				try {
					// *** do not allow overwriting of destination file
					if (!overwrite) {
						File destFile = new File(destinationFile);
						if (destFile.exists()) {
							this.setErrorCode(ERR_FILEALREADYTHERE);
							return Status.CANCEL_STATUS;
						}
					}
					
					String[] command = {
						"cmd",
					};
					Process p = Runtime.getRuntime().exec(command);
					SyncPipeCopyCD pipe =
						new SyncPipeCopyCD(monitor, p.getErrorStream(), System.err);
					pipe.start();
					PrintWriter stdin = new PrintWriter(p.getOutputStream());
					// *** create the command
					String devTargetCommand = "";
					if ((device != null) && (!device.isEmpty()))
						devTargetCommand = " dev=" + device + " ";
					stdin.println(commandBase + "readcd " + devTargetCommand + " -f "
						+ destinationFile);
					System.out.println(destinationFile);
					stdin.close();
					
					// start the process and wait for completion
					int returnCode = p.waitFor();
					
					// *** analyze the output of stderr to find out if the operation was successful
					// if something like
					// "Read 411424.00 kB at 5407.3 kB/sec." can be found, then the copy was
					// successful
					Pattern resultMatcher = Pattern.compile(successfulReadCdMatcher);
					Matcher matcher = resultMatcher.matcher(lastStdErrLine);
					if (matcher.find()) {
						this.setErrorCode(ERR_NOERR);
						monitor.done();
						return Status.OK_STATUS;
					} else {
						this.setErrorCode(ERR_COPYNOTSUCCESSFUL);
						monitor.done();
						return Status.CANCEL_STATUS;
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
					this.setErrorCode(ERR_INTERRUPTEDEXCEPTION);
					return Status.CANCEL_STATUS;
				} catch (IOException e) {
					e.printStackTrace();
					this.setErrorCode(ERR_IOEXCEPTION);
					return Status.CANCEL_STATUS;
				}
			}
		};
		// *** start the job
		job1.schedule();
		
		// *** return the job to the caller
		return job1;
	}
	
	/**
	 * helper class for getting output of command line tools
	 * 
	 * @author marlovitsh
	 * 
	 */
	static class SyncPipeCopyCD extends Thread {
		private final OutputStream ostrm_;
		private final InputStream istrm_;
		private String theOutput = "";
		
		private int capacity = 0;
		private boolean foundCapacity = false;
		protected IProgressMonitor monitor_;
		
		/**
		 * 
		 * @param monitor
		 *            the ProgressMonitor used for showing the progress of the copying
		 * @param istrm
		 *            the input stream from which we will read
		 * @param ostrm
		 *            the output stream to which we will write sequentially
		 * @param writeToStd
		 *            should the method write to stdout/err...
		 */
		public SyncPipeCopyCD(IProgressMonitor monitor, InputStream istrm, OutputStream ostrm){
			istrm_ = istrm;
			ostrm_ = ostrm;
			theOutput = "";
			monitor_ = monitor;
		}
		
		public void run(){
			try {
				int current = 0;
				final byte[] buffer = new byte[1024];
				for (int length = 0; (length = istrm_.read(buffer)) != -1;) {
					// *** enable cancelling of this job
					if (monitor_.isCanceled()) {
						monitor_.done();
						// ++++ doesn't work ???
						break;
					}
					lastStdErrLine = new String(buffer, "UTF-8");
					lastStdErrLine = lastStdErrLine.substring(0, length);
					theOutput = theOutput + lastStdErrLine;
					// *** try to find capacity of disk, can be found in:
					// Capacity: 385696 Blocks = 771392 kBytes = 753 MBytes = 789 prMB Sectorsize:
					// 2048 Bytes
					if (!foundCapacity) {
						Pattern resultMatcher = Pattern.compile(capacityReadCDMatcher);
						Matcher matcher = resultMatcher.matcher(lastStdErrLine);
						if (matcher.find()) {
							capacity = Integer.parseInt(matcher.group(1));
							currCDCopyMax = capacity;
							foundCapacity = true;
							monitor_.beginTask(COPY_CD_TO_ISO_JOBNAME, currCDCopyMax);
							String progressString =
								String.format(COPY_CD_PROGRESSSTRING, 0, currCDCopyMax, 0);
							monitor_.subTask(progressString);
						}
					}
					Pattern resultMatcher = Pattern.compile(progressReadCDMatcher);
					Matcher matcher = resultMatcher.matcher(lastStdErrLine);
					if (matcher.find()) {
						if (foundCapacity) {
							int oldCurrent = current;
							current = Integer.parseInt(matcher.group(1));
							currCDCopyCounter = current;
							String progressString =
								String.format(COPY_CD_PROGRESSSTRING, current, capacity,
									((current * 100) / capacity));
							monitor_.subTask(progressString);
							monitor_.worked(current - oldCurrent);
						}
					}
					Pattern resultMatcher2 = Pattern.compile(successfulReadCdMatcher);
					Matcher matcher2 = resultMatcher2.matcher(lastStdErrLine);
					if (matcher2.find()) {
						if (foundCapacity) {
							System.out.println(capacity + "/" + capacity + "  100%");
							currCDCopyCounter = capacity;
							String progressString =
								String.format(COPY_CD_PROGRESSSTRING, capacity, capacity, 100);
							monitor_.subTask(progressString);
							monitor_.worked(currCDCopyMax - current);
						}
					}
					
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (foundCapacity) {
					monitor_.done();
				}
			}
		}
		
		/**
		 * returns the whole output in one single string
		 * 
		 * @return the string...
		 */
		public String getOutput(){
			return theOutput;
		}
	}
	
	/**
	 * helper class for getting output of command line tools
	 * 
	 * @author marlovitsh
	 * 
	 */
	static class SyncPipe implements Runnable {
		private final OutputStream ostrm_;
		private final InputStream istrm_;
		private boolean writeToStd_;
		private String theOutput = "";
		
		/**
		 * 
		 * @param istrm
		 *            the input stream from which we will read
		 * @param ostrm
		 *            the output stream to which we will write sequentially
		 * @param writeToStd
		 *            should the method write to stdout/err...
		 */
		public SyncPipe(InputStream istrm, OutputStream ostrm, boolean writeToStd){
			istrm_ = istrm;
			ostrm_ = ostrm;
			writeToStd_ = writeToStd;
			theOutput = "";
		}
		
		public void run(){
			try {
				final byte[] buffer = new byte[1024];
				for (int length = 0; (length = istrm_.read(buffer)) != -1;) {
					lastStdErrLine = new String(buffer, "UTF-8");
					lastStdErrLine = lastStdErrLine.substring(0, length);
					if (writeToStd_)
						ostrm_.write(buffer, 0, length);
					theOutput = theOutput + lastStdErrLine;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		/**
		 * returns the whole output in one single string
		 * 
		 * @return the string...
		 */
		public String getOutput(){
			return theOutput;
		}
	}
	
	/**
	 * Eject a cd. If no device is specified, then the routine tries to find a device. If only one
	 * device is present, then this one is used. If more than one device is present, the routine
	 * returns an error.
	 * 
	 * @param device
	 *            the device to be used - something like 0,1,0 or 0,0,0
	 * @return 0 if successful, != 0 on error
	 */
	
	public static int ejectCD(final String device){
		JobWithErrorCode job1 = new JobWithErrorCode(COPY_CD_TO_ISO_JOBNAME) {
			@Override
			protected IStatus run(IProgressMonitor monitor){
				try {
					// *** call process and get output of stderr - if something like
					// "Read 411424.00 kB at 5407.3 kB/sec." can be
					// found, then the copy was successful
					String[] command = {
						"cmd",
					};
					Process p = Runtime.getRuntime().exec(command);
					new Thread(new SyncPipe(p.getErrorStream(), System.err, true)).start();
					PrintWriter stdin = new PrintWriter(p.getOutputStream());
					// *** create the command
					String devTargetCommand = "";
					if ((device != null) && (!device.isEmpty()))
						devTargetCommand = " dev=" + device + " ";
					stdin.println(commandBase + "cdrecord " + devTargetCommand + " -eject");
					stdin.close();
					int returnCode = p.waitFor();
					// try to find <ejectErrorMatcher>
					Pattern resultMatcher = Pattern.compile(ejectErrorMatcher);
					Matcher matcher2 = resultMatcher.matcher(lastStdErrLine);
					if (matcher2.find())
						return Status.CANCEL_STATUS;
						//return ERR_EJECT;
					else
						return Status.OK_STATUS;
						//return ERR_NOERR;
				} catch (InterruptedException e) {
					e.printStackTrace();
					return Status.CANCEL_STATUS;
					//return ERR_INTERRUPTEDEXCEPTION;
				} catch (IOException e2) {
					e2.printStackTrace();
					return Status.CANCEL_STATUS;
					//return ERR_IOEXCEPTION;
				}
			}
		};
		// *** start the job
		job1.schedule();
		
		// *** for now, just ignore errors
		return ERR_NOERR;
	}
	
	// ********* JOB version for SyncPipeCopyCD
	// static class SyncPipeCopyCD_Job extends Job {
	// private final OutputStream ostrm_;
	// private final InputStream istrm_;
	// private boolean writeToStd_;
	// private String theOutput = "";
	//
	// private int capacity = 0;
	// private boolean foundCapacity = false;
	//
	// public SyncPipeCopyCD_Job(String name, InputStream istrm, OutputStream ostrm,
	// boolean writeToStd){
	// super(name);
	// istrm_ = istrm;
	// ostrm_ = ostrm;
	// writeToStd_ = writeToStd;
	// theOutput = "";
	// }
	//
	// @Override
	// protected IStatus run(IProgressMonitor monitor){
	// try {
	// int current = 0;
	// final byte[] buffer = new byte[1024];
	// System.out.println("run called");
	// for (int length = 0; (length = istrm_.read(buffer)) != -1;) {
	// System.out.println("run called loop");
	// TimeUnit.MILLISECONDS.sleep(500);
	//
	// lastStdErrLine = new String(buffer, "UTF-8");
	// lastStdErrLine = lastStdErrLine.substring(0, length);
	// // if (writeToStd_)
	// // ostrm_.write(buffer, 0, length);
	// theOutput = theOutput + lastStdErrLine;
	// // *** try to find capacity of disk, can be found in:
	// // Capacity: 385696 Blocks = 771392 kBytes = 753 MBytes = 789 prMB Sectorsize:
	// // 2048 Bytes
	// if (!foundCapacity) {
	// Pattern resultMatcher = Pattern.compile("Capacity: ([0-9]+) Blocks");
	// Matcher matcher = resultMatcher.matcher(lastStdErrLine);
	// if (matcher.find()) {
	// capacity = Integer.parseInt(matcher.group(1));
	// currCDCopyMax = capacity;
	// foundCapacity = true;
	// monitor.beginTask("testing again", currCDCopyMax);
	// monitor.subTask("0 / " + currCDCopyMax + "  0%");
	// }
	// }
	// Pattern resultMatcher = Pattern.compile("addr:[ ]+([0-9]+) cnt: [0-9]+");
	// Matcher matcher = resultMatcher.matcher(lastStdErrLine);
	// if (matcher.find()) {
	// if (foundCapacity) {
	// int oldCurrent = current;
	// current = Integer.parseInt(matcher.group(1));
	// System.out.println(current + "/" + capacity + "  "
	// + ((current * 100) / capacity) + "%");
	// monitor.subTask(current + "/" + capacity + "  "
	// + ((current * 100) / capacity) + "%");
	// monitor.worked(current - oldCurrent);
	// currCDCopyCounter = current;
	// }
	// }
	// Pattern resultMatcher2 = Pattern.compile(successfulReadCdMatcher);
	// Matcher matcher2 = resultMatcher2.matcher(lastStdErrLine);
	// if (matcher2.find()) {
	// if (foundCapacity) {
	// System.out.println(capacity + "/" + capacity + "  100%");
	// currCDCopyCounter = capacity;
	// monitor.subTask(capacity + "/" + capacity + "  100%");
	// monitor.worked(currCDCopyMax - current);
	// }
	// }
	//
	// }
	// } catch (Exception e) {
	// e.printStackTrace();
	// return Status.CANCEL_STATUS;
	// }
	// System.out.println("run end");
	// return Status.OK_STATUS;
	// }
	//
	// }
	
	// *********************************************************************
	// *********************************************************************
	// *********************************************************************
	// *************************** internal testing
	public static void main(String[] args){
		String progressString = String.format(COPY_CD_PROGRESSSTRING, 1, 2, 3);
		System.out.println(progressString);
		
		if (1 == 1)
			return;
		System.out.println("***************");
		for (FileStore store : FileSystems.getDefault().getFileStores()) {
			System.out.printf("%s: %s  isReadOnly: %s%n", store.name(), store.type(),
				store.isReadOnly());
		}
		System.out.println("***************");
		
		System.out.println("%%%%%%%%%%%%%%%%%");
		File[] paths;
		FileSystemView fsv = FileSystemView.getFileSystemView();
		
		// returns pathnames for files and directory
		paths = File.listRoots();
		
		// for each pathname in pathname array
		for (File path : paths) {
			// prints file and directory paths
			System.out.println("Drive Name: " + path);
			System.out.println("Description: " + fsv.getSystemTypeDescription(path));
		}
		System.out.println("%%%%%%%%%%%%%%%%%");
		
		// List <File>files = Arrays.asList(File.listRoots());
// for (File f : files) {
// String s1 = FileSystemView.getFileSystemView().getSystemDisplayName (f);
// String s2 = FileSystemView.getFileSystemView().getSystemTypeDescription(f);
// System.out.println("getSystemDisplayName : " + s1);
// System.out.println("getSystemTypeDescription : " + s2);
// }
	}
	
}