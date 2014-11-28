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
package org.netbeans.modules.python.editor.imports;

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.gsf.api.CancellableTask;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.SourceModel;
import org.netbeans.modules.gsf.api.SourceModelFactory;
import org.netbeans.modules.gsf.spi.GsfUtilities;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.PythonIndex;
import org.netbeans.modules.python.editor.lexer.PythonTokenId;
import org.openide.ErrorManager;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.python.antlr.PythonTree;

/**
 * Python fast import library. Based on the Java one by Jan Lahoda.
 *
 * @todo When you insert an import, attempt to add it in the right sorted
 *   place.
 *
 * @author Tor Norbye
 */
public class FastImportAction extends BaseAction {
    private static final String ACTION_NAME = "fast-import";

    /** Creates a new instance of FastImportAction */
    public FastImportAction() {
        super(ACTION_NAME);
    }

    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        try {
            final Rectangle caretRectangle = target.modelToView(target.getCaretPosition());
            final Font font = target.getFont();
            final Point where = new Point(caretRectangle.x, caretRectangle.y + caretRectangle.height);
            SwingUtilities.convertPointToScreen(where, target);

            final int position = target.getCaretPosition();
            final String ident = Utilities.getIdentifier(Utilities.getDocument(target), position);
            FileObject file = GsfUtilities.findFileObject(target.getDocument());

            if (ident == null || file == null) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            SourceModel model = SourceModelFactory.getInstance().getModel(file);
            if (model != null) {
                final CompilationInfo[] infoHolder = new CompilationInfo[1];
                try {
                    model.runUserActionTask(new CancellableTask<CompilationInfo>() {
                        public void cancel() {
                        }

                        public void run(CompilationInfo info) throws Exception {
                            importItem(info, where, caretRectangle, font, position, ident);
                        }
                    }, false);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

        } catch (BadLocationException ex) {
            ErrorManager.getDefault().notify(ex);
        }
    }

    private void importItem(final CompilationInfo info, final Point where, final Rectangle caretRectangle, final Font font, final int position, final String ident) {
        PythonTree root = PythonAstUtils.getRoot(info);
        if (root == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        // Compute suggestions
        PythonIndex index = PythonIndex.get(info.getIndex(PythonTokenId.PYTHON_MIME_TYPE), info.getFileObject());
        Set<String> modules = index.getImportsFor(ident, true);

        // TODO - check the file to pick a better default (based on existing imports, usages of the symbol
        // which implies which alternative is eligible, etc.)


        final List<String> privileged = new ArrayList<String>(modules);
        Collections.sort(privileged);
        final List<String> denied = new ArrayList<String>();
        Collections.sort(denied);

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                ImportModulePanel panel = new ImportModulePanel(ident, privileged, denied, font, info, position);
                PopupUtil.showPopup(panel, "", where.x, where.y, true, caretRectangle.height);
            }
        });
    }
}
