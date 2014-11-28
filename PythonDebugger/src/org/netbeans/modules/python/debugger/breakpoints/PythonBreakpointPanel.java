/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
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
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */
package org.netbeans.modules.python.debugger.breakpoints;

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.JPanel;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;

import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.modules.python.debugger.Utils;
import org.netbeans.spi.debugger.ui.Controller;

import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.Line;
import org.openide.text.NbDocument;
import org.openide.util.NbBundle;

/**
 * Panel for customizing Python breakpoints.
 *
 * @author  Jean-Yves Mengant
 */
public class PythonBreakpointPanel extends JPanel implements Controller, org.openide.util.HelpCtx.Provider {

  private final static String _URLFILE_ = "file://" ;

  private ConditionsPanel _conditionsPanel;
  private PythonBreakpoint _breakpoint;
  private boolean _createBreakpoint = false;

  private void initPane(PythonBreakpoint b) {

    _breakpoint = b;
    initComponents();

    if (b != null) {
      String url = b.getFilePath();
      tfFileName.setText(url);
    }
    tfFileName.setPreferredSize(new Dimension(
            30 * tfFileName.getFontMetrics(tfFileName.getFont()).charWidth('W'),
            tfFileName.getPreferredSize().height));

    if (b != null) {
      tfLineNumber.setText(Integer.toString(b.getLineNumber()));
    }
    _conditionsPanel = new ConditionsPanel(null);
    setupConditionPane();
    if (b != null) {
      _conditionsPanel.setCondition(b.getCondition());
      _conditionsPanel.setHitCountFilteringStyle(b.getHitCountFilteringStyle());
      _conditionsPanel.setHitCount(b.getHitCountFilter());
    }
    cPanel.add(_conditionsPanel, "Center");
  }

  /** Creates new form LineBreakpointPanel **/
  public PythonBreakpointPanel() {
    this.initPane(null);
    _createBreakpoint = true;
  }

  /** Creates new form LineBreakpointPanel */
  public PythonBreakpointPanel(PythonBreakpoint b) {
    this.initPane(b);
  }

  private static int findNumLines(String url) {
    FileObject file;
    try {
      file = URLMapper.findFileObject(new URL(url));
    } catch (MalformedURLException e) {
      return 0;
    }
    if (file == null) {
      return 0;
    }
    DataObject dataObject;
    try {
      dataObject = DataObject.find(file);
    } catch (DataObjectNotFoundException ex) {
      return 0;
    }
    EditorCookie ec = (EditorCookie) dataObject.getCookie(EditorCookie.class);
    if (ec == null) {
      return 0;
    }
    ec.prepareDocument().waitFinished();
    Document d = ec.getDocument();
    if (!(d instanceof StyledDocument)) {
      return 0;
    }
    StyledDocument sd = (StyledDocument) d;
    return NbDocument.findLineNumber(sd, sd.getLength());
  }

  private void setupConditionPane() {
    if ( _breakpoint != null )
        _conditionsPanel.setupConditionPaneContext(_breakpoint.getFilePath(), _breakpoint.getLineNumber());
  }

  public org.openide.util.HelpCtx getHelpCtx() {
    return new org.openide.util.HelpCtx("NetbeansDebuggerBreakpointLineJPDA"); // NOI18N
    }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    pSettings = new javax.swing.JPanel();
    jLabel3 = new javax.swing.JLabel();
    tfFileName = new javax.swing.JTextField();
    jLabel1 = new javax.swing.JLabel();
    tfLineNumber = new javax.swing.JTextField();
    cPanel = new javax.swing.JPanel();
    pActions = new javax.swing.JPanel();
    jPanel1 = new javax.swing.JPanel();

    setLayout(new java.awt.GridBagLayout());

    java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/netbeans/modules/python/debugger/breakpoints/Bundle"); // NOI18N
    pSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("L_Line_Breakpoint_BorderTitle"))); // NOI18N
    pSettings.setLayout(new java.awt.GridBagLayout());

    jLabel3.setLabelFor(tfFileName);
    org.openide.awt.Mnemonics.setLocalizedText(jLabel3, bundle.getString("L_Line_Breakpoint_File_Name")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 3);
    pSettings.add(jLabel3, gridBagConstraints);
    jLabel3.getAccessibleContext().setAccessibleDescription(bundle.getString("ACSD_L_Line_Breakpoint_File_Name")); // NOI18N

    tfFileName.setEditable(false);
    tfFileName.setToolTipText(bundle.getString("TTT_TF_Line_Breakpoint_File_Name")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 3);
    pSettings.add(tfFileName, gridBagConstraints);
    tfFileName.getAccessibleContext().setAccessibleDescription(bundle.getString("ACSD_TF_Line_Breakpoint_File_Name")); // NOI18N

    jLabel1.setLabelFor(tfLineNumber);
    org.openide.awt.Mnemonics.setLocalizedText(jLabel1, bundle.getString("L_Line_Breakpoint_Line_Number")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 3);
    pSettings.add(jLabel1, gridBagConstraints);
    jLabel1.getAccessibleContext().setAccessibleDescription(bundle.getString("ACSD_L_Line_Breakpoint_Line_Number")); // NOI18N

    tfLineNumber.setToolTipText(bundle.getString("TTT_TF_Line_Breakpoint_Line_Number")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 3);
    pSettings.add(tfLineNumber, gridBagConstraints);
    tfLineNumber.getAccessibleContext().setAccessibleName("Line number");
    tfLineNumber.getAccessibleContext().setAccessibleDescription(bundle.getString("ACSD_TF_Line_Breakpoint_Line_Number")); // NOI18N

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    add(pSettings, gridBagConstraints);

    cPanel.setLayout(new java.awt.BorderLayout());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    add(cPanel, gridBagConstraints);

    pActions.setLayout(new java.awt.BorderLayout());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    add(pActions, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(jPanel1, gridBagConstraints);

    getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(PythonBreakpointPanel.class, "ACSN_LineBreakpoint")); // NOI18N
  }// </editor-fold>//GEN-END:initComponents

  // Controller implementation ...............................................
  /**
   * Called when "Ok" button is pressed.
   *
   * @return whether customizer can be closed
   */
  public boolean ok() {
    String msg = validateMsg();
    if (msg == null) {
      msg = _conditionsPanel.validateMsg();
    }
    if (msg != null) {
      DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(msg));
      return false;
    }
    // actionsPanel.ok ();
    if (_breakpoint == null) {
      Line line = Utils.getLine(tfFileName.getText(), Integer.parseInt(tfLineNumber.getText().trim()));
      _breakpoint = new PythonBreakpoint(line);
    } else {
      _breakpoint.setLine( buildUrl(tfFileName.getText()) , Integer.parseInt(tfLineNumber.getText().trim()));
    }
    _breakpoint.setCondition(_conditionsPanel.getCondition());
    _breakpoint.setHitCountFilter(_conditionsPanel.getHitCount(),
            _conditionsPanel.getHitCountFilteringStyle());

    if (_createBreakpoint) {
      DebuggerManager.getDebuggerManager().addBreakpoint(_breakpoint);
    }
    else
      _breakpoint.fireUpdated() ;

    return true;
  }

  /**
   * Called when "Cancel" button is pressed.
   *
   * @return whether customizer can be closed
   */
  public boolean cancel() {
    return true;
  }

  /**
   * Return <code>true</code> whether value of this customizer
   * is valid (and OK button can be enabled).
   *
   * @return <code>true</code> whether value of this customizer
   * is valid
   */
  @Override
  public boolean isValid() {
    return true;
  }

  private String buildUrl( final String fName ) {
    if ( fName.startsWith(_URLFILE_ ) ) {
      return fName ;
    }
    return _URLFILE_ + fName ;

  }

  private String validateMsg() {
    int line;
    try {
      line = Integer.parseInt(tfLineNumber.getText().trim());
    } catch (NumberFormatException e) {
      return NbBundle.getMessage(PythonBreakpointPanel.class, "MSG_No_Line_Number_Spec");
    }
    if (line <= 0) {
      return NbBundle.getMessage(PythonBreakpointPanel.class, "MSG_NonPositive_Line_Number_Spec");
    }
    int maxLine = findNumLines( buildUrl ( _breakpoint.getFilePath()) );
    if (maxLine == 0) { // Not found
      maxLine = Integer.MAX_VALUE; // Not to bother the user when we did not find it
    }
    if (line > maxLine + 1) {
      return NbBundle.getMessage(PythonBreakpointPanel.class, "MSG_TooBig_Line_Number_Spec",
              Integer.toString(line), Integer.toString(maxLine + 1));
    }
    return null;
  }
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel cPanel;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel pActions;
  private javax.swing.JPanel pSettings;
  private javax.swing.JTextField tfFileName;
  private javax.swing.JTextField tfLineNumber;
  // End of variables declaration//GEN-END:variables
}
