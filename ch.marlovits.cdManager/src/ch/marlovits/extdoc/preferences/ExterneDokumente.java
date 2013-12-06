/*******************************************************************************
 * Copyright (c) 2006, Daniel Lutz and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Daniel Lutz - initial implementation
 *    
 *  $Id: ExterneDokumente.java 5639 2009-08-17 15:47:53Z rgw_ch $
 *******************************************************************************/
package ch.marlovits.extdoc.preferences;

// +++++ Another change

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import ch.elexis.Hub;
import ch.elexis.preferences.SettingsPreferenceStore;

/**
 * Einstellungen zur Verknüpfung externen Dokumenten
 * @author Daniel Lutz
 */
public class ExterneDokumente extends FieldEditorPreferencePage implements
		IWorkbenchPreferencePage {

	public ExterneDokumente() {
		super(GRID);
		setPreferenceStore(new SettingsPreferenceStore(Hub.localCfg));
		setDescription("Externe Dokumente");
	}

	@Override
	protected void createFieldEditors() {
		DirectoryFieldEditor dfe;
		
		dfe=new DirectoryFieldEditor(PreferenceConstants.BASIS_PFAD,
				"Verzeichnis für externe Dokumente", getFieldEditorParent());
		addField(dfe);
		
		dfe=new DirectoryFieldEditor(PreferenceConstants.BASIS_PFAD2,
				"Verzeichnis für externe Dokumente", getFieldEditorParent());
		addField(dfe);

		dfe=new DirectoryFieldEditor(PreferenceConstants.BASIS_PFAD3,
				"Verzeichnis für externe Dokumente", getFieldEditorParent());
		addField(dfe);
	}

	public void init(IWorkbench workbench) {
	}
}
