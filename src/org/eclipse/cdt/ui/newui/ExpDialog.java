/*******************************************************************************
 * Copyright (c) 2007 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Intel Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.ui.newui;

//import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;

public class ExpDialog extends AbstractPropertyDialog {
	
	protected static final String TO_ALL = "Apply to all"; //$NON-NLS-1$

	public String[] sel_types = null;
	public String[] sel_langs = null; 
	private Text txt1;
	private Text txt2;
	private List langs;
	private List types;
	private Label message;
	private Button c_langs;
	private Button c_types;
	private Button c_all;
	
	private Button b_vars;
	private Button b_work;
	private Button b_file;
	private Button b_ok;
	private Button b_ko;
	private boolean newAction;
	private int kind;
	private ICConfigurationDescription cfgd;
	private String[] names_l, names_t;
	private java.util.List existing;

	public ExpDialog(Shell parent, boolean _newAction,
		String title, String _data1, String _data2,
		ICConfigurationDescription _cfgd,
		String[] _langs, String[] _types,
		int _kind, String[] _names_l, String[] _names_t, 
		java.util.List _existing) {
		super(parent, title);
		super.text1 = (_data1 == null) ? EMPTY_STR : _data1;
		super.text2 = (_data2 == null) ? EMPTY_STR : _data2;
		newAction = _newAction;
		cfgd = _cfgd;
		sel_langs = _langs;
		sel_types = _types;
		kind = _kind;
		names_l = _names_l;
		names_t = _names_t;
		existing = _existing;
	}

	protected Control createDialogArea(Composite c) {
		c.setLayout(new GridLayout(4, true));
		GridData gd;
		
		Label l1 = new Label(c, SWT.NONE);
		l1.setText("Name:");  //$NON-NLS-1$
		l1.setLayoutData(new GridData(GridData.BEGINNING));
		
		txt1 = new Text(c, SWT.SINGLE | SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 3;
		txt1.setLayoutData(gd);
		txt1.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				setButtons();
			}});
				
		Label l2 = new Label(c, SWT.NONE);
		l2.setText("Value:");  //$NON-NLS-1$
		l2.setLayoutData(new GridData(GridData.BEGINNING));
		
		txt2 = new Text(c, SWT.SINGLE | SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		txt2.setLayoutData(gd);
		txt2.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				setButtons();
			}});
		
		if (kind != ICSettingEntry.MACRO) {
			l1.setVisible(false);
			txt1.setVisible(false);
			txt2.setText(super.text1); // note that name edited by 2nd text widget
		} else {
			txt1.setText(super.text1);
			txt2.setText(super.text1);
			if (!newAction) txt1.setEnabled(false); // macro name
		}
		
		b_vars = setupButton(c, AbstractCPropertyTab.VARIABLESBUTTON_NAME);

		c_all = new Button(c, SWT.CHECK);
		c_all.setText(NewUIMessages.getResourceString("ExpDialog.0")); //$NON-NLS-1$
		gd = new GridData(GridData.BEGINNING);
		gd.horizontalSpan = 2;
		c_all.setLayoutData(gd);
		c_all.setVisible(newAction);
		
		b_work = setupButton(c, AbstractCPropertyTab.WORKSPACEBUTTON_NAME);
		b_file = setupButton(c, AbstractCPropertyTab.FILESYSTEMBUTTON_NAME);
		if (kind == ICSettingEntry.MACRO) {
			b_work.setVisible(false);
			b_file.setVisible(false);
		}
		
		Group dest = new Group(c, SWT.NONE);
		dest.setText(NewUIMessages.getResourceString("ExpDialog.1")); //$NON-NLS-1$
		dest.setLayout(new GridLayout(2, true));
		gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = 4;
		dest.setLayoutData(gd);
		
		Label l = new Label(dest, SWT.NONE);
		l.setText(NewUIMessages.getResourceString("ExpDialog.2")); //$NON-NLS-1$
		l.setLayoutData(new GridData(GridData.BEGINNING));

		l = new Label(dest, SWT.NONE);
		l.setText(NewUIMessages.getResourceString("ExpDialog.3")); //$NON-NLS-1$
		l.setLayoutData(new GridData(GridData.BEGINNING));
		
		c_langs = new Button(dest, SWT.CHECK);
		c_langs.setText(TO_ALL);
		c_langs.setLayoutData(new GridData(GridData.BEGINNING));
		c_langs.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				langs.setEnabled(!c_langs.getSelection());
			}});
		
		c_types = new Button(dest, SWT.CHECK);
		c_types.setText(TO_ALL);
		c_types.setLayoutData(new GridData(GridData.BEGINNING));
		c_types.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				types.setEnabled(!c_types.getSelection());
			}});
		
		langs = new List(dest, SWT.BORDER | SWT.MULTI);
		langs.setLayoutData(new GridData(GridData.FILL_BOTH));
		langs.setItems(names_l);
		setSelections(sel_langs, langs, c_langs);
		
		types = new List(dest, SWT.BORDER | SWT.MULTI);
		types.setLayoutData(new GridData(GridData.FILL_BOTH));
		types.setItems(names_t);
		setSelections(sel_types, types, c_types);
		
		message = new Label(c, SWT.NONE);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 4;
		message.setLayoutData(gd);
	    message.setForeground(c.getDisplay().getSystemColor(SWT.COLOR_RED));
		
		// DUMMY PLACEHOLDER
		new Label(c, 0).setLayoutData(new GridData(GridData.BEGINNING));
		b_ok = setupButton(c, IDialogConstants.OK_LABEL);
		b_ko = setupButton(c, IDialogConstants.CANCEL_LABEL);

		c.getShell().setDefaultButton(b_ok);
		c.pack();
		Rectangle r = shell.getBounds();
		r.width = 400;
		shell.setBounds(r);
		setButtons();
		return c;
	}	
	
	private void setButtons() {
		if (b_ok == null) return; // while init only
		message.setText(EMPTY_STR);
		String name;
		boolean enabled = true;
		if (kind == ICSettingEntry.MACRO) 
			name  = txt1.getText().trim();
		else 
			name = txt2.getText().trim();
		if (name.length() == 0) {
			enabled = false;
			message.setText("Name cannot be empty !"); //$NON-NLS-1$
		}
		if (enabled && existing != null && existing.contains(name)) {
			message.setText("The same name already exists !"); //$NON-NLS-1$
			enabled = false;
		}
		b_ok.setEnabled(enabled);
	}
	
	public void buttonPressed(SelectionEvent e) {
		String s;
		if (e.widget.equals(b_ok)) { 
			if (kind == ICSettingEntry.MACRO)
				super.text1 = txt1.getText();
			super.text2 = txt2.getText();
			super.check1 = c_all.getSelection();
			sel_langs = (c_langs.getSelection()) ? null : langs.getSelection();
			sel_types = (c_types.getSelection()) ? null : types.getSelection();
			result = true;
			shell.dispose(); 
		} else if (e.widget.equals(b_ko)) {
			shell.dispose();
		} else if (e.widget.equals(b_vars)) {
			s = AbstractCPropertyTab.getVariableDialog(shell, cfgd);
			if (s != null) txt2.insert(s);
		}  else if (e.widget.equals(b_work)) {
			if (kind == ICSettingEntry.INCLUDE_PATH ||
				kind == ICSettingEntry.LIBRARY_PATH)
				s = AbstractCPropertyTab.getWorkspaceDirDialog(shell, txt2.getText());
			else 
				s = AbstractCPropertyTab.getWorkspaceFileDialog(shell, txt2.getText());
			if (s != null) {
				s = strip_wsp(s);
				txt2.setText(s);
				text1 = s; // to be compared with final text
			}
		} else if (e.widget.equals(b_file)) {
			if (kind == ICSettingEntry.INCLUDE_PATH ||
				kind == ICSettingEntry.LIBRARY_PATH)
				s = AbstractCPropertyTab.getFileSystemDirDialog(shell, txt2.getText());
			else 
				s = AbstractCPropertyTab.getFileSystemFileDialog(shell, txt2.getText());
			if (s != null) txt2.setText(s);
		}
	}
	
	protected void setSelections(String[] sel, List lst, Button check) {
		if (sel == null) {
			lst.setEnabled(false);
			check.setSelection(true);
		} else {
			lst.setEnabled(true);
			check.setSelection(false);
			if (sel.length == 0) return;
			int cnt = 0;
			String[] items = lst.getItems();

			int[] indices = new int[items.length];
			for (int i=0; i<indices.length; i++) indices[i] = -1;
			
			for (int i=0; i<items.length; i++) {
				for (int j=0; j<sel.length; j++) {
					if (items[i].equals(sel[j])) {
						indices[cnt++] = i;
						break;
					}
				}
			}
			lst.setSelection(indices);
		}
	}

}