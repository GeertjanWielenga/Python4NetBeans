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

import java.io.CharConversionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.swing.tree.TreeNode;
import org.netbeans.modules.gsf.api.ParserResult.AstTreeNode;
import org.openide.util.Exceptions;
import org.openide.xml.XMLUtil;
import org.python.antlr.PythonTree;
import org.python.antlr.Visitor;
import org.python.antlr.ast.Name;

class PythonAstTreeNode implements AstTreeNode {
    private List<PythonAstTreeNode> children;
    private final PythonTree node;
    private final PythonAstTreeNode parent;

    private PythonAstTreeNode(PythonAstTreeNode parent, PythonTree node) {
        super();
        this.parent = parent;
        this.node = node;
        if (parent != null) {
            if (parent.children == null) {
                parent.children = new ArrayList<PythonAstTreeNode>();
            }
            parent.children.add(this);
        }
    }

    public Object getAstNode() {
        return node;
    }

    public int getStartOffset() {
        return node.getCharStartIndex();
    }

    public int getEndOffset() {
        return node.getCharStopIndex();
    }

    public TreeNode getChildAt(int childIndex) {
        // Nope, this doesn't pick up all nodes!! Use node traversal as
        // done in the PythonAstOffsetsTest instead!
        if (children == null) {
            return null;
        }
        return children.get(childIndex);
    }

    public int getChildCount() {
        return children != null ? children.size() : 0;
    }

    public TreeNode getParent() {
        return parent;
    }

    public int getIndex(TreeNode node) {
        if (children != null) {
            for (int i = 0, n = children.size(); i < n; i++) {
                PythonAstTreeNode child = children.get(i);
                if (node == child) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean getAllowsChildren() {
        return false;
    }

    public boolean isLeaf() {
        return node.getChildCount() == 0;
    }

    public Enumeration children() {
        if (children == null) {
            return Collections.enumeration(Collections.emptyList());
        } else {
            return Collections.enumeration(children);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(XMLUtil.toElementContent(node.toString()));
        } catch (CharConversionException ex) {
            Exceptions.printStackTrace(ex);
        }
        //sb.append("<i>"); // NOI18N
        sb.append(" (");
        sb.append(getStartOffset());
        sb.append("-");
        sb.append(getEndOffset());
        sb.append(") ");
        //sb.append("</i>"); // NOI18N

        if (node instanceof Name) {
            sb.append('"');
            sb.append(((Name)node).getInternalId());
            sb.append('"');
        }

        return sb.toString();
    }

    private static class AstNodeInitializer extends Visitor {
        private List<PythonAstTreeNode> stack = new ArrayList<PythonAstTreeNode>();
        private PythonAstTreeNode root;

        @Override
        public void traverse(PythonTree node) throws Exception {
            PythonAstTreeNode parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);

            PythonAstTreeNode astNode = new PythonAstTreeNode(parent, node);
            if (parent == null) {
                root = astNode;
            }
            stack.add(astNode);
            super.traverse(node);
            stack.remove(stack.size() - 1);
        }

        public PythonAstTreeNode getRoot() {
            return root;
        }
    }

    // Factory
    static PythonAstTreeNode get(PythonTree tree) {
        if (tree != null) {
            AstNodeInitializer visitor = new AstNodeInitializer();
            try {
                visitor.visit(tree);
                return visitor.getRoot();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return null;
    }
}
