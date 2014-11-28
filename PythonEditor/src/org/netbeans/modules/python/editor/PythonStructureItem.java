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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.ImageIcon;
import org.netbeans.modules.python.editor.elements.AstElement;
import org.netbeans.modules.gsf.api.ElementHandle;
import org.netbeans.modules.gsf.api.ElementKind;
import org.netbeans.modules.gsf.api.HtmlFormatter;
import org.netbeans.modules.gsf.api.Modifier;
import org.netbeans.modules.gsf.api.StructureItem;
import org.netbeans.modules.python.editor.scopes.SymbolTable;
import org.openide.util.ImageUtilities;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.FunctionDef;

public final class PythonStructureItem extends AstElement implements StructureItem {
    private List<PythonStructureItem> children;
    private PythonStructureItem parent;

    public PythonStructureItem(SymbolTable scopes, PythonTree node, String name, ElementKind kind) {
        super(scopes, node, name, kind);
        this.node = node;
        this.name = name;
        this.kind = kind;
    }

    void add(PythonStructureItem child) {
        if (children == null) {
            children = new ArrayList<PythonStructureItem>();
        }
        children.add(child);
        child.parent = this;
    }

    public String getSortText() {
        return name;
    }

    public String getHtml(HtmlFormatter formatter) {
        formatter.appendText(name);
        if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
            FunctionDef def = (FunctionDef)node;
            List<String> params = PythonAstUtils.getParameters(def);
            if (params.size() > 0) {
                boolean isFirst = true;
                formatter.appendHtml("(");
                formatter.parameters(true);
                for (String param : params) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        formatter.appendText(",");
                    }
                    formatter.appendText(param);
                }
                formatter.parameters(false);
                formatter.appendHtml(")");
            }
        }
        return formatter.getText();
    }

    public ElementHandle getElementHandle() {
        return null;
    }

    public boolean isLeaf() {
        return children == null;
    }

    public List<? extends StructureItem> getNestedItems() {
        return children == null ? Collections.<StructureItem>emptyList() : children;
    }

    public long getPosition() {
        return node.getCharStartIndex();
    }

    public long getEndPosition() {
        return node.getCharStopIndex();
    }

    public ImageIcon getCustomIcon() {
        if (kind == ElementKind.CLASS && getModifiers().contains(Modifier.PRIVATE)) {
            // GSF doesn't automatically handle icons on private classes, so I have to
            // work around that here
            return ImageUtilities.loadImageIcon("org/netbeans/modules/python/editor/resources/private-class.png", false); //NOI18N
        }

        return null;
    }

    @Override
    public Object getSignature() {
        if ((kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) && parent != null &&
                parent.kind == ElementKind.CLASS) {
            return parent.name + "." + name;
        }
        return super.getSignature();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PythonStructureItem other = (PythonStructureItem)obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        if (this.kind != other.kind) {
            return false;
        }
        if (this.getModifiers() != other.getModifiers() && (this.modifiers == null || !this.modifiers.equals(other.modifiers))) {
            return false;
        }

        if ((kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) && node != null && other.node != null) {
            FunctionDef def = (FunctionDef)node;
            List<String> params = PythonAstUtils.getParameters(def);
            List<String> otherParams = PythonAstUtils.getParameters((FunctionDef)other.node);
            if (!params.equals((otherParams))) {
                return false;
            }
        }

//        if (this.getNestedItems().size() != other.getNestedItems().size()) {
//            return false;
//        }
//
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (this.name != null ? this.name.hashCode() : 0);
        return hash;
    }
}
