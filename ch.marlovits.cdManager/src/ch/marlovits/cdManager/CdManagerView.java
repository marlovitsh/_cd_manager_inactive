package ch.marlovits.cdManager;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.Hub;
import ch.elexis.actions.BackgroundJob;
import ch.elexis.actions.ElexisEvent;
import ch.elexis.actions.ElexisEventDispatcher;
import ch.elexis.actions.ElexisEventListenerImpl;
import ch.elexis.actions.GlobalActions;
import ch.elexis.actions.GlobalEventDispatcher;
import ch.elexis.actions.JobPool;
import ch.elexis.actions.BackgroundJob.BackgroundJobListener;
import ch.elexis.actions.GlobalEventDispatcher.IActivationListener;
import ch.elexis.data.Patient;
import ch.elexis.data.PersistentObject;
import ch.elexis.util.Log;
import ch.elexis.util.SWTHelper;
import ch.marlovits.extdoc.dialogs.FileEditDialog;
import ch.marlovits.extdoc.dialogs.VerifierDialog;
import ch.marlovits.extdoc.preferences.PreferenceConstants;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

public class CdManagerView extends ViewPart implements IActivationListener {
	private Button copyButton;
	protected Composite parent_ = null;
	
	/**
	 * The constructor.
	 */
	public CdManagerView(){}
	
	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent){
		// my testing button
		final Composite pathArea = new Composite(parent, SWT.NONE);
		pathArea.setLayout(new GridLayout(3, false));

		copyButton = new Button(pathArea, SWT.NONE);
		copyButton.setText("copy CD/DVD");
		copyButton.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e){
				JobWithErrorCode copyJob = CDLibrary.copyCDToIso("MyJobName", "", "Z:\\cds\\fromJava_2.iso", true);
				copyJob.getState();
				if (copyJob.getState() == Job.NONE ) {
					// job is done - now test if the job is SUCCESSFULLY done:
					int errCode = copyJob.getErrorCode();
					if (errCode != CDLibrary.ERR_NOERR)
						System.out.println ("Error: " + errCode);
					else 
						System.out.println ("NO ERROR - SUCCESSFUL");
					} 
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e){}
		});
	}
	
	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus(){}
	
	/**
	 * Wichtig! Alle Listeners, die eine View einhängt, müssen in dispose() wieder ausgehängt
	 * werden. Sonst kommt es zu Exceptions, wenn der Anwender eine View schliesst und später ein
	 * Objekt selektiert.
	 */
	@Override
	public void dispose(){
		GlobalEventDispatcher.removeActivationListener(this, this);
	}
	
	// Die Methode des SelectionListeners
	public void selectionEvent(PersistentObject obj){
		if (obj instanceof Patient) {}
	}
	
	// Die beiden Methoden des ActivationListeners
	/**
	 * Die View wird aktiviert (z.B angeklickt oder mit Tab)
	 */
	public void activation(boolean mode){
		/* Interessiert uns nicht */
	}
	
	/**
	 * Die View wird sichtbar (mode=true). Immer dann hängen wir unseren SelectionListener ein.
	 * (Benutzeraktionen interessieren uns ja nur dann, wenn wir etwas damit machen müssen, also
	 * sichtbar sind. Im unsichtbaren Zustand würde das Abfangen von SelectionEvents nur unnötig
	 * Ressourcen verbrauchen. Aber weil es ja sein könnte, dass der Anwender, während wir im
	 * Hintergrund waren, etliche Aktionen durchgefürt hat, über die wir jetzt nicht informiert
	 * sind, "simulieren" wir beim Sichtbar-Werden gleich einen selectionEvent, um uns zu
	 * infomieren, welcher Patient jetzt gerade selektiert ist.
	 * 
	 * Oder die View wird unsichtbar (mode=false). Dann hängen wir unseren SelectionListener aus und
	 * faulenzen ein wenig.
	 */
	public void visible(boolean mode){
		if (mode == true) {} else {}
	}
	
	class LongRunningOperation extends Thread {
		private Display display;
		
		private ProgressBar progressBar;
		
		public LongRunningOperation(Display display, ProgressBar progressBar){
			this.display = display;
			this.progressBar = progressBar;
		}
		
		public void run(){
			for (int i = 0; i < 30; i++) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
				display.asyncExec(new Runnable() {
					public void run(){
						if (progressBar.isDisposed())
							return;
						progressBar.setSelection(progressBar.getSelection() + 1);
					}
				});
			}
		}
	}
	
	public class MyJob extends Job {
		Composite parent_;
		
		public MyJob(String name, Composite parent){
			super(name);
			setUser(true);
			parent_ = parent;
		}
		
		protected IStatus run(final IProgressMonitor monitor){
			try {
				monitor.beginTask("Running Job ...", 30);
				System.out.println("Job wird ausgeführt");
				
				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run(){
						for (int i = 0; i < 30; i++) {
							final int i_ = i;
							try {
								Thread.sleep(1000);
								parent_.getDisplay().asyncExec(new Runnable() {
									public void run(){
										// monitor.subTask("i = " + i_);
										monitor.worked(1);
										System.out.println("called...");
									}
								});
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						monitor.done();
					}
				});
				return new Status(Status.OK, "myPlug-In", "Job finished");
			} catch (Exception e) {
				e.printStackTrace();
				return new Status(Status.ERROR, "myPlug-In", "An error occured during job");
			} finally {
				System.out.println("*** done ***");
			}
		}
	}
}
