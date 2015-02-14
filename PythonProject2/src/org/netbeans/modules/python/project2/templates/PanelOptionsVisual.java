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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
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


package org.netbeans.modules.python.project2.templates;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.StringTokenizer;
import javax.swing.JComboBox;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import org.netbeans.modules.python.api.PlatformComponentFactory;
import org.netbeans.modules.python.api.PythonPlatform;
import org.netbeans.modules.python.api.PythonPlatformManager;
import org.netbeans.modules.python.api.Util;
import org.netbeans.modules.python.project2.ui.Utils;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.InstanceDataObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.CallableSystemAction;

/**
 * @author  Tomas Zezula
 */
public final class PanelOptionsVisual extends SettingsPanel implements ActionListener, PropertyChangeListener {

    private static boolean lastMainClassCheck = true; // XXX Store somewhere

    private PanelConfigureProject panel;
    private boolean valid;
    private String projectLocation;

    public PanelOptionsVisual(PanelConfigureProject panel, NewPythonProjectWizardIterator.WizardType type) {
        initComponents();
        this.panel = panel;
        PlatformComponentFactory.addPlatformChangeListener(platforms, new PlatformComponentFactory.PlatformChangeListener() {
            @Override
            public void platformChanged() {
                fireChangeEvent();
            }
        });


        switch (type) {
            case APP:
                createMainCheckBox.addActionListener( this );
                createMainCheckBox.setSelected( lastMainClassCheck );
                mainFileTextField.setEnabled( lastMainClassCheck );
                break;
        }

        this.mainFileTextField.getDocument().addDocumentListener( new DocumentListener () {

            @Override
            public void insertUpdate(DocumentEvent e) {
                mainFileChanged ();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                mainFileChanged ();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                mainFileChanged ();
            }

        });

    }

    @Override
    public void actionPerformed( ActionEvent e ) {
        if ( e.getSource() == createMainCheckBox ) {
            lastMainClassCheck = createMainCheckBox.isSelected();
            mainFileTextField.setEnabled( lastMainClassCheck );
            this.panel.fireChangeEvent();
        }
    }

    @Override
    public void propertyChange (PropertyChangeEvent event) {
        // The project name isn't very python'y
        if (NewPythonProjectWizardIterator.PROP_PROJECT_NAME.equals(event.getPropertyName())) {
            String newProjectName = (String) event.getNewValue();
            this.mainFileTextField.setText (NbBundle.getMessage(PanelOptionsVisual.class, "TXT_MainFileName",newProjectName.toLowerCase()));
        }
        if (NewPythonProjectWizardIterator.PROP_PROJECT_LOCATION.equals(event.getPropertyName())) {
            projectLocation = (String)event.getNewValue();
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        createMainCheckBox = new javax.swing.JCheckBox();
        mainFileTextField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        platforms = org.netbeans.modules.python.api.PlatformComponentFactory.getPythonPlatformsComboxBox();
        manage = new javax.swing.JButton();

        createMainCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(createMainCheckBox, org.openide.util.NbBundle.getBundle(PanelOptionsVisual.class).getString("LBL_createMainCheckBox")); // NOI18N

        mainFileTextField.setText("main");

        jLabel1.setLabelFor(platforms);
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(PanelOptionsVisual.class, "TXT_PythonPlatform")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(manage, org.openide.util.NbBundle.getMessage(PanelOptionsVisual.class, "TXT_ManagePlatfroms")); // NOI18N
        manage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(createMainCheckBox)
                    .addComponent(jLabel1))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(platforms, javax.swing.GroupLayout.PREFERRED_SIZE, 233, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(manage)
                        .addGap(4, 4, 4))
                    .addComponent(mainFileTextField)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(createMainCheckBox)
                    .addComponent(mainFileTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel1)
                        .addComponent(platforms, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(manage))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        createMainCheckBox.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getBundle(PanelOptionsVisual.class).getString("ACSN_createMainCheckBox")); // NOI18N
        createMainCheckBox.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getBundle(PanelOptionsVisual.class).getString("ACSD_createMainCheckBox")); // NOI18N
        mainFileTextField.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getBundle(PanelOptionsVisual.class).getString("ASCN_mainClassTextFiled")); // NOI18N
        mainFileTextField.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getBundle(PanelOptionsVisual.class).getString("ASCD_mainClassTextFiled")); // NOI18N

        getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(PanelOptionsVisual.class, "ACSN_PanelOptionsVisual")); // NOI18N
        getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(PanelOptionsVisual.class, "ACSD_PanelOptionsVisual")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

private void manageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageActionPerformed
//Workaround, Needs an API to display platform customizer
    final FileObject fo = FileUtil.getConfigFile("Actions/Python/org-netbeans-modules-python-platform-PythonManagerAction.instance");  //NOI18N
    if (fo != null) {
        try {
            InstanceDataObject ido = (InstanceDataObject) DataObject.find(fo);
            CallableSystemAction action = (CallableSystemAction) ido.instanceCreate();
            action.performAction();
            platforms.setModel(Utils.createPlatformModel()); //Currentl the PythonManager doesn't fire events, we need to replace model.
        } catch (IOException | ClassNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}//GEN-LAST:event_manageActionPerformed



    @Override
    boolean valid(WizardDescriptor settings) {
        if (PlatformComponentFactory.getPlatform(platforms) == null) {
            // Only complain if there are no available platforms since most likely there's
            // no selection yet because it's busy auto-detecting
            if (PythonPlatformManager.getInstance().getPlatformList().isEmpty()) {
                settings.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE,
                        NbBundle.getMessage(PanelOptionsVisual.class,"ERROR_IllegalPlatform"));
            }
            return false;
        }
        if (mainFileTextField.isVisible () && mainFileTextField.isEnabled ()) {
            if (!valid) {
                settings.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE,
                    NbBundle.getMessage(PanelOptionsVisual.class,"ERROR_IllegalMainFileName")); //NOI18N
            }
            return this.valid;
        }
        else {
            return true;
        }
    }

    @Override
    void read (WizardDescriptor d) {
        final PythonPlatformManager manager = PythonPlatformManager.getInstance();
        String pid = (String) d.getProperty(NewPythonProjectWizardIterator.PROP_PLATFORM_ID);
        if (pid == null) {
            pid = Util.getPythonPreferences().get(LAST_PLATFORM_ID, null);
            if (pid == null) {
                pid = manager.getDefaultPlatform();
            }
        }
        final PythonPlatform activePlatform = manager.getPlatform(pid);
        if (activePlatform != null) {
            platforms.setSelectedItem(activePlatform);
        }
    }

    @Override
    void validate (WizardDescriptor d) throws WizardValidationException {
        // nothing to validate
    }

    @Override
    void store( WizardDescriptor d ) {
        d.putProperty(NewPythonProjectWizardIterator.PROP_MAIN_FILE, createMainCheckBox.isSelected() && createMainCheckBox.isVisible() ? mainFileTextField.getText() : null ); // NOI18N
        PythonPlatform platform = PlatformComponentFactory.getPlatform(platforms);
        if (platform != null) {
            d.putProperty(NewPythonProjectWizardIterator.PROP_PLATFORM_ID, platform.getId());
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox createMainCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextField mainFileTextField;
    private javax.swing.JButton manage;
    private javax.swing.JComboBox platforms;
    // End of variables declaration//GEN-END:variables

    private void mainFileChanged () {
        String mainClassName = this.mainFileTextField.getText ();
        StringTokenizer tk = new StringTokenizer (mainClassName, "."); //NOI18N
        boolean isJavaIdentifier = true;
        while (tk.hasMoreTokens()) {
            String token = tk.nextToken();
            if (token.length() == 0 || !Utilities.isJavaIdentifier(token)) {
                isJavaIdentifier = false;
                break;
            }
        }
        this.valid = isJavaIdentifier;
        this.panel.fireChangeEvent();
    }

    public @Override void removeNotify() {
        storeWizardPlatform(platforms);
        super.removeNotify();
    }

    private void fireChangeEvent() {
        this.panel.fireChangeEvent();
    }

    private static final String LAST_PLATFORM_ID = "projectPanelLastPlatformID"; // NOI18N

    public static void preselectWizardPlatform(final JComboBox platforms) {
        preselectPlatform(platforms, LAST_PLATFORM_ID);
    }

    public static void preselectPlatform(final JComboBox platforms, final String preferencePlatformIDKey) {
        String lastPlatformID = Util.getPythonPreferences().get(preferencePlatformIDKey, null);
        if (lastPlatformID != null) {
            PythonPlatform platform = PythonPlatformManager.getInstance().getPlatform(lastPlatformID);
            if (platform != null) {
                platforms.setSelectedItem(platform);
            }
        }
    }

    public static void storeWizardPlatform(JComboBox platforms) {
        PythonPlatform selectedPlatform = PlatformComponentFactory.getPlatform(platforms);
        if (selectedPlatform != null) {
            Util.getPythonPreferences().put(LAST_PLATFORM_ID, selectedPlatform.getId());
        }
    }

//
//    public static void storeWizardPlatform(JComboBox platforms) {
//        PythonPlatform selectedPlatform = PlatformComponentFactory.getPlatform(platforms);
//        if (selectedPlatform != null) {
//            RubyPreferences.getPreferences().put(LAST_PLATFORM_ID, selectedPlatform.getID());
//        }
//    }
}

