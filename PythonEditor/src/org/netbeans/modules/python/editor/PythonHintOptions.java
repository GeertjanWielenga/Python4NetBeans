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
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.python.editor;

import org.netbeans.modules.python.editor.lexer.PythonTokenId;
import org.netbeans.modules.gsf.api.HintsProvider;
import org.netbeans.modules.gsf.api.HintsProvider.HintsManager;
import org.netbeans.spi.options.AdvancedOption;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;

/**
 * Hint settings for Python
 */
public class PythonHintOptions extends AdvancedOption {
    OptionsPanelController panelController;

    public String getDisplayName() {
        return NbBundle.getMessage(PythonHintOptions.class, "CTL_Hints_DisplayName"); // NOI18N
    }

    public String getTooltip() {
        return NbBundle.getMessage(PythonHintOptions.class, "CTL_Hints_ToolTip"); // NOI18N
    }

    public synchronized OptionsPanelController create() {
        if (panelController == null) {
            HintsManager manager = HintsProvider.Factory.getManager(PythonTokenId.PYTHON_MIME_TYPE);
            assert manager != null;
            panelController = manager.getOptionsController();
        }

        return panelController;
    }

    //TODO: temporary solution, this should be solved on GSF level
    public static OptionsPanelController createStatic() {
        return new PythonHintOptions().create();
    }
}
