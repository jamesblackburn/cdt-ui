/**********************************************************************
 * Copyright (c) 2007 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *     QNX Software Systems - Initial API and implementation
 **********************************************************************/

package org.eclipse.cdt.internal.ui.ffs;

import java.io.File;
import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ide.fileSystem.FileSystemContributor;

/**
 * @author Doug Schaefer
 *
 */
public class FFSFileSystemContributor extends FileSystemContributor {

	public URI browseFileSystem(String initialPath, Shell shell) {
		DirectoryDialog dialog = new DirectoryDialog(shell);
		dialog.setMessage("Select Project Location");

		if (!initialPath.equals("")) { //$NON-NLS-1$
			IFileInfo info = EFS.getLocalFileSystem().getStore(new Path(initialPath)).fetchInfo();
			if (info != null && info.exists()) {
				dialog.setFilterPath(initialPath);
			}
		}

		String selectedDirectory = dialog.open();
		if (selectedDirectory == null) {
			return null;
		}
		return new File(selectedDirectory).toURI();
	}

}