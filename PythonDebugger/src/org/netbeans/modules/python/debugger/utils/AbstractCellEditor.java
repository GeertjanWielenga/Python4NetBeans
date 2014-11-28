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
package org.netbeans.modules.python.debugger.utils;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;

/**
 *
 * @author Jean-Yves Mengant
 */
public class AbstractCellEditor implements TableCellEditor, TreeCellEditor {

  protected EventListenerList listenerList = new EventListenerList();
  protected Object value;
  protected ChangeEvent changeEvent = null;
  protected int clickCountToStart = 1;

  /** Returns the value contained in the editor**/
  public Object getCellEditorValue() {
    return value;
  }

  public void setCellEditorValue(Object value) {
    this.value = value;
  }

  public void setClickCountToStart(int count) {
    clickCountToStart = count;
  }

  public int getClickCountToStart() {
    return clickCountToStart;
  }

  /**
   * Ask the editor if it can start editing using <I>anEvent</I>.
   * <I>anEvent</I> is in the invoking component coordinate system.
   * The editor can not assume the Component returned by
   * getCellEditorComponent() is installed.  This method is intended
   * for the use of client to avoid the cost of setting up and installing
   * the editor component if editing is not possible.
   * If editing can be started this method returns true.
   *
   * @param	anEvent		the event the editor should use to consider
   *				whether to begin editing or not.
   * @return	true if editing can be started.
   * @see #shouldSelectCell
   */
  public boolean isCellEditable(EventObject anEvent) {
    if (anEvent instanceof MouseEvent) {
      if (((MouseEvent) anEvent).getClickCount() < clickCountToStart) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tell the editor to start editing using <I>anEvent</I>.  It is
   * up to the editor if it want to start editing in different states
   * depending on the exact type of <I>anEvent</I>.  For example, with
   * a text field editor, if the event is a mouse event the editor
   * might start editing with the cursor at the clicked point.  If
   * the event is a keyboard event, it might want replace the value
   * of the text field with that first key, etc.  <I>anEvent</I>
   * is in the invoking component's coordinate system.  A null value
   * is a valid parameter for <I>anEvent</I>, and it is up to the editor
   * to determine what is the default starting state.  For example,
   * a text field editor might want to select all the text and start
   * editing if <I>anEvent</I> is null.  The editor can assume
   * the Component returned by getCellEditorComponent() is properly
   * installed in the clients Component hierarchy before this method is
   * called. <p>
   *
   * The return value of shouldSelectCell() is a boolean indicating whether
   * the editing cell should be selected or not.  Typically, the return
   * value is true, because is most cases the editing cell should be
   * selected.  However, it is useful to return false to keep the selection
   * from changing for some types of edits.  eg. A table that contains
   * a column of check boxes, the user might want to be able to change
   * those checkboxes without altering the selection.  (See Netscape
   * Communicator for just such an example)  Of course, it is up to
   * the client of the editor to use the return value, but it doesn't
   * need to if it doesn't want to.
   *
   * @param	anEvent		the event the editor should use to start
   *				editing.
   * @return	true if the editor would like the editing cell to be selected
   * @see #isCellEditable
   */
  public boolean shouldSelectCell(EventObject anEvent) {
    if (this.isCellEditable(anEvent)) {
      if (anEvent == null || ((MouseEvent) anEvent).getClickCount() >= clickCountToStart) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tell the editor to stop editing and accept any partially edited
   * value as the value of the editor.  The editor returns false if
   * editing was not stopped, useful for editors which validates and
   * can not accept invalid entries.
   *
   * @return	true if editing was stopped
   */
  public boolean stopCellEditing() {
    fireEditingStopped();
    return true;
  }

  /**
   * Tell the editor to cancel editing and not accept any partially
   * edited value.
   */
  public void cancelCellEditing() {
    fireEditingCanceled();
  }

  /**
   * Add a listener to the list that's notified when the editor starts,
   * stops, or cancels editing.
   *
   * @param	l		the CellEditorListener
   */
  public void addCellEditorListener(CellEditorListener l) {
    listenerList.add(CellEditorListener.class, l);
  }

  /**
   * Remove a listener from the list that's notified
   *
   * @param	l		the CellEditorListener
   */
  public void removeCellEditorListener(CellEditorListener l) {
    listenerList.remove(CellEditorListener.class, l);
  }

  /**
   * Sets an initial <I>value</I> for the editor.  This will cause
   * the editor to stopEditing and lose any partially edited value
   * if the editor is editing when this method is called. <p>
   *
   * Returns the component that should be added to the client's
   * Component hierarchy.  Once installed in the client's hierarchy
   * this component will then be able to draw and receive user input.
   *
   * @param	table		the JTree that is asking the editor to edit
   *				This parameter can be null.
   * @param	value		the value of the cell to be edited.
   * @param	isSelected	true is the cell is to be renderer with
   *				selection highlighting
   * @param	expanded	true if the node is expanded
   * @param	leaf		true if the node is a leaf node
   * @param	row		the row index of the node being edited
   * @return	the component for editing
   */
  public Component getTreeCellEditorComponent(JTree tree, Object myvalue, boolean isSelected,
          boolean expanded, boolean leaf, int row) {
    return null;
  }

  /**
   *  Sets an initial <I>value</I> for the editor.  This will cause
   *  the editor to stopEditing and lose any partially edited value
   *  if the editor is editing when this method is called. <p>
   *
   *  Returns the component that should be added to the client's
   *  Component hierarchy.  Once installed in the client's hierarchy
   *  this component will then be able to draw and receive user input.
   *
   * @param	table		the JTable that is asking the editor to edit
   *				This parameter can be null.
   * @param	value		the value of the cell to be edited.  It is
   *				up to the specific editor to interpret
   *				and draw the value.  eg. if value is the
   *				String "true", it could be rendered as a
   *				string or it could be rendered as a check
   *				box that is checked.  null is a valid value.
   * @param	isSelected	true is the cell is to be renderer with
   *				selection highlighting
   * @param	row     	the row of the cell being edited
   * @param	column  	the column of the cell being edited
   * @return	the component for editing
   */
  public Component getTableCellEditorComponent(JTable table, Object myvalue, boolean isSelected,
          int row, int column) {
    return null;
  }

  protected void fireEditingStopped() {
    Object[] listeners = listenerList.getListenerList();

    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == CellEditorListener.class) {
        if (changeEvent == null) {
          changeEvent = new ChangeEvent(this);
        }

        ((CellEditorListener) listeners[i + 1]).editingStopped(changeEvent);
      }
    }
  }

  protected void fireEditingCanceled() {
    Object[] listeners = listenerList.getListenerList();

    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == CellEditorListener.class) {
        if (changeEvent == null) {
          changeEvent = new ChangeEvent(this);
        }

        ((CellEditorListener) listeners[i + 1]).editingCanceled(changeEvent);
      }
    }
  }
}
