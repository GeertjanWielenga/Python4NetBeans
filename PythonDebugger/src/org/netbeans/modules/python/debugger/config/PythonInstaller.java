/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2008 Sun Microsystems, Inc.
 */
package org.netbeans.modules.python.debugger.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.Enumeration;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import org.netbeans.modules.python.debugger.PythonDebugException;
import org.netbeans.modules.python.debugger.PythonDebugParameters;
import org.netbeans.modules.python.debugger.gui.PythonDebugContainer;

/**
 * automatically install and check necessary Python resources for the IDE
 *
 * @author jean-yves Mengant
 */
public class PythonInstaller {
  // private final static String _PYLOC_   = "python/";
  // moving back to root 
  // used for old cleaning old  

  private final static String _PYLOC_ = "/org/netbeans/modules/python/debugger/resources/python/nbpythondebug/";
  private final static String _DEST_PYLOC_ = "nbpythondebug/";
  private final static String _LOCATOR_ = "MANIFEST";
  private final static String _JPYDBGSCRIPT_ = "jpydaemon.py";
  private final static String _WORK_ = "tmp";
  private final static String _VERSIONPREFIX_ = "nbPython_debugger_";
  private final static String _PYSUFFIX_ = ".py";

  /**
   * Creates a new instance of PythonInstaller
   */
  public PythonInstaller() {
  }

  private String getDestPythonDir() {
    return PythonDebugParameters.getSettingsDirectory() + '/' + _DEST_PYLOC_;
  }

  private File getPythonWorkDir() {
    String dir = PythonDebugParameters.getSettingsDirectory() + '/' + _WORK_;
    File fDir = new File(dir);
    if (fDir.exists()) {
      return fDir;
    }
    fDir.mkdir();
    return fDir;
  }

  private void cleanupPythonDir(File pythonDir) {
    if (pythonDir.isDirectory()) {
      String fileNames[] = pythonDir.list();
      for (int ii = 0; ii < fileNames.length; ii++) {
        if ((fileNames[ii].startsWith(_VERSIONPREFIX_)) ||
                (fileNames[ii].endsWith(_PYSUFFIX_))) {
          File f = new File(pythonDir, fileNames[ii]);
          f.delete();
        }
      }
    }
  }

  private void cleanupVersions(File vFName) {
    File curDir = vFName.getParentFile();
    // ./nbpythondebug
    cleanupPythonDir(curDir);

  }

  private File getFileNameVersion() {
    return new File(
            getDestPythonDir(),
            NetBeansFrontend.getVersion().replace(' ', '_'));
  }

  private boolean checkVersion() {
    File versionFile = getFileNameVersion();
    if (versionFile.isFile()) {
      return true;
    } else {
      // cleanup previous version file and return false
      // to install new  
      cleanupVersions(versionFile);
      return false;
    }
  }

  private InputStream getResourceFromDistrib(String name) {
    return this.getClass().getResourceAsStream(name);
  }

  /**
   * Check that the correct version of JpyDbg Python stuff is in place
   *
   * @return true if in place
   */
  public boolean checkInPlace() {
    return checkVersion();
  }

  private void populatePythonDirectory(InputStream myStream)
          throws PythonDebugException {
    BufferedReader rdr =
            new BufferedReader(new InputStreamReader(myStream));
    Vector list = new Vector();
    try {
      String curPython = rdr.readLine();
      while (curPython != null) {
        if (curPython.trim().length() > 0) {
          list.addElement(curPython);
        }
        curPython = rdr.readLine();
      }
      rdr.close();

    } catch (IOException e) {
      throw new PythonDebugException(
              " IO Error reading Manifest in populatePythonDirectory");
    }

    // just use to monitor python setup
    _MONITOR_ mon = new _MONITOR_(list.size());

    Enumeration pyList = list.elements();
    int counter = 0;
    while (pyList.hasMoreElements()) {
      String curPy = (String) pyList.nextElement();
      InputStream cur = getResourceFromDistrib(_PYLOC_ + curPy);
      if (cur == null) {
        throw new PythonDebugException(_PYLOC_ + curPy + " resource missing in JpyDbg distribution");
      }

      BufferedReader in = new BufferedReader(new InputStreamReader(cur));
      try {
        File fDir = new File(getDestPythonDir());
        if (!fDir.exists()) {
          fDir.mkdirs();
        } else if (!fDir.isDirectory()) // give up when python is not a subdir !!!!!!
        {
          throw new PythonDebugException(fDir.toString() + " exists but is not a Directory => Giving up on nbPython debugger setup");
        }

        File f = new File(
                fDir,
                curPy);
        BufferedWriter dest = new BufferedWriter(new FileWriter(f));
        String line = in.readLine();
        while (line != null) {
          dest.write(line);
          dest.newLine();
          line = in.readLine();
        }
        in.close();
        dest.close();
      } catch (IOException e) {
        throw new PythonDebugException(
                "nbPython : severe IOError populating python directory : " + e.getMessage());

      }
      mon.statusChanged(++counter, "File " + curPy + " processed ");
    }

    try {

      // Finally acknowledge by writing an empty VersionFile inside directory
      BufferedWriter dest =
              new BufferedWriter(new FileWriter(getFileNameVersion()));
      dest.close();
    } catch (IOException e) {
      throw new PythonDebugException(
              "nbPython : severe IOError writing :" + getFileNameVersion() + "=" + e.getMessage());

    }


  }

  /**
   * Put the /python requested utilities at the right place under the
   * configuration directory , in a dedicated Python subfolder from the
   * distribution jar ./Python subfolder where the pythonsetup.txt describes the
   * list of python sources to install This process is JEdit / Netbeans
   * compliant and must be activated on plugin load
   *
   * @throws PythonDebugException When any configuration problem is encountererd
   */
  public void putInPlace() {

    if (!checkInPlace()) {
      InputStream myStream = getResourceFromDistrib(_PYLOC_ + _LOCATOR_);
      try {
        if (myStream == null) {
          throw new PythonDebugException(
                  _PYLOC_ + _LOCATOR_ +
                  " not in nbpython debugger module => JpyDbg Packaging error");
        }
        populatePythonDirectory(myStream);
      } catch (PythonDebugException e) {
        JOptionPane.showMessageDialog(null, "Python setup error = " + e.getMessage(), "Severe nbPython Setup Error", JOptionPane.ERROR_MESSAGE);
      }
    }
    // populate the jpyDbg directory accordingly + setup a tmp work directory as well
    File f = new File(getDestPythonDir(), _JPYDBGSCRIPT_);
    if (!f.exists() || f.isDirectory()) {
      JOptionPane.showMessageDialog(null, "Python setup error : missing jpydbgdaemon = " + f.toString(), "Severe nbPython Setup Error", JOptionPane.ERROR_MESSAGE);
    }

    // set JpyDbgLocation
    PythonDebugParameters.set_jpydbgScript(f.getAbsolutePath());
    // set working directory
    PythonDebugParameters.set_tempDir(getPythonWorkDir().getAbsolutePath());
  }

  class _MONITOR_ extends Thread {

    private ProgressMonitor _pBar;
    private int _size;

    public _MONITOR_(int size) {
      _size = size;
      SwingUtilities.invokeLater(this);
    }

    @Override
    public void run() {
      _pBar = new ProgressMonitor(
              null,
              "Upgrading nbPython_debugger Python's stuff for " + PythonDebugContainer.VERSION,
              "Initializing ...",
              0,
              _size);

    }

    private void statusChanged(
            final int counter,
            final String message) {
      SwingUtilities.invokeLater(
              new Runnable() {

                public void run() {
                  _pBar.setProgress(counter);
                  _pBar.setNote(message);
                }
              });
    }
  }
}
