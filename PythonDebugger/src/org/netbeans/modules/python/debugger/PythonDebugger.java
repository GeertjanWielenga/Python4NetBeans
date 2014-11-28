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
 */package org.netbeans.modules.python.debugger;

import org.netbeans.api.debugger.ActionsManager;

import org.netbeans.spi.debugger.ActionsProviderSupport;
import org.netbeans.spi.debugger.ContextProvider;
import org.netbeans.spi.debugger.DebuggerEngineProvider;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import java.util.HashSet;
import java.util.Set;
import org.netbeans.modules.python.debugger.actions.JpyDbgView;
import org.netbeans.modules.python.debugger.backend.DebuggerContextChangeListener;
import org.netbeans.modules.python.debugger.backend.PluginEvent;
import org.netbeans.modules.python.debugger.spi.PythonEvent;
import org.netbeans.modules.python.debugger.spi.PythonSession;
import org.netbeans.modules.python.debugger.spi.PythonSourceDebuggee;
import org.netbeans.modules.python.debugger.spi.SessionsModel;
import org.netbeans.modules.python.debugger.gui.PythonDebugContainer;
import org.netbeans.modules.python.debugger.gui.PythonVariableTreeDataNode;
import org.netbeans.api.debugger.Session;

/**
 * Netbeans Python / Jython Debugger Core
 *
 * @author jean-yves Mengant
 */
public class PythonDebugger
        extends ActionsProviderSupport {

  /** Name of property for state of debugger. */
  public static final String PROP_STATE = "state";
  /** Name of property for current thread. */
  public static final String PROP_CURRENT_THREAD = "currentThread";
  /** Name of property for current stack frame. */
  public static final String PROP_CURRENT_CALL_STACK_FRAME =
          "currentCallStackFrame";
  /** Property name constant. */
  public static final String PROP_SUSPEND = "suspend"; // NOI18N
  /** Debugger state constant. */
  public static final int STATE_STARTING = 1;
  /** Debugger state constant. */
  public static final int STATE_RUNNING = 2;
  /** Debugger state constant. */
  public static final int STATE_STOPPED = 3;
  /** Debugger state constant. */
  public static final int STATE_DISCONNECTED = 4;
  private final static String[] _STATES_ = new String[]{
    "Inactive",
    "Starting",
    "Running",
    "Stopped",
    "Disconnected"
  };
  private SessionsModel _sModel = null;
  // ActionsProvider .........................................................
  private static Set<Object> _ACTIONS_ = new HashSet<Object>();
  

  static {
    _ACTIONS_.add(ActionsManager.ACTION_KILL);
    _ACTIONS_.add(ActionsManager.ACTION_CONTINUE);
    _ACTIONS_.add(ActionsManager.ACTION_START);
    _ACTIONS_.add(ActionsManager.ACTION_STEP_INTO);
    _ACTIONS_.add(ActionsManager.ACTION_STEP_OVER);
    _ACTIONS_.add(ActionsManager.ACTION_STEP_OUT);
    _ACTIONS_.add(ActionsManager.ACTION_RUN_TO_CURSOR);
  }
  private ContextProvider _contextProvider;
  private Object _currentLine;
  private PythonDebuggerEngineProvider _engineProvider;
  private PythonSourceDebuggee _pyCookie;
  private PythonSession _pySession = null;
  private boolean _breakPointsPopulated = false;
  private int _state = 0;
  private PropertyChangeSupport _pcs;

  /**
   * Creates a new instance of PythonDebugger
   */
  public PythonDebugger(ContextProvider contextProvider) {
    _pcs = new PropertyChangeSupport(this);

    System.out.println("entering constructor: PythonDebugger");
    _contextProvider = contextProvider;

    // init engineProvider
    _engineProvider = (PythonDebuggerEngineProvider) contextProvider.lookupFirst(null, DebuggerEngineProvider.class);

    // init pythonCookie
    _pyCookie = contextProvider.lookupFirst(null, PythonSourceDebuggee.class);

    // init actions
    setEnabled(ActionsManager.ACTION_KILL, true);
    setEnabled(ActionsManager.ACTION_STEP_INTO, true);
    setEnabled(ActionsManager.ACTION_STEP_OVER, true);
    setEnabled(ActionsManager.ACTION_STEP_OUT, true);
    setEnabled(ActionsManager.ACTION_CONTINUE, true);
    setEnabled(ActionsManager.ACTION_START, true);
    setEnabled(ActionsManager.ACTION_RUN_TO_CURSOR, true);

  }

  /**
   * DOCUMENT ME!
   *
   * @return DOCUMENT ME!
   */
  public Set getActions() {
    return _ACTIONS_;
  }

  /**
   * handle ACTION_START event : this means that a debugger session is starting
   * nothing special is expected to happen here the 'real start' of jpydbg will
   * happen in a separate thread which will activate the taskStarted method (see
   * taskStarted method for details)
   */
  private void doStart() {

    // Notify Starting
    _pyCookie.getDebugView().writeLog("PythonDebugger : overall Starting");
  }

  private void finish() {
    Utils.unmarkCurrent();

    // Ask JpyDbg to terminate debugging session
    _pyCookie.getDebugView().terminateSession(
            new PythonEvent(PythonEvent.STOP_SESSION, _pySession));
  }

  private void doStep(Object action) {
    if (action == ActionsManager.ACTION_STEP_INTO) {
      _pyCookie.getDebugView().stepInto();
    } else if (action == ActionsManager.ACTION_STEP_OUT) {
      _pyCookie.getDebugView().stepOut();
    } else if (action == ActionsManager.ACTION_STEP_OVER) {
      _pyCookie.getDebugView().stepOver();
    }
  }

  private void enterRunningState() {
    setState(STATE_RUNNING);
    setEnabled(ActionsManager.ACTION_STEP_INTO, false);
    setEnabled(ActionsManager.ACTION_STEP_OVER, false);
    setEnabled(ActionsManager.ACTION_STEP_OUT, false);
    setEnabled(ActionsManager.ACTION_CONTINUE, false);
    setEnabled(ActionsManager.ACTION_RUN_TO_CURSOR, false);
  }

  private void leaveRunningState() {
    setState(STATE_STOPPED);
    setEnabled(ActionsManager.ACTION_STEP_INTO, true);
    setEnabled(ActionsManager.ACTION_STEP_OVER, true);
    setEnabled(ActionsManager.ACTION_STEP_OUT, true);
    setEnabled(ActionsManager.ACTION_CONTINUE, true);
    setEnabled(ActionsManager.ACTION_RUN_TO_CURSOR, true);
  }

  private void doContinue( boolean toCursor ) {
    Utils.unmarkCurrent();
    _pyCookie.getDebugView().doContinue(toCursor);
  }

  /**
   * Called from DebuggerAntLogger.
   */
  public void pythonFinished(PythonEvent event) {
    _engineProvider.getDestructor().killEngine();
    Utils.unmarkCurrent();
    setState(STATE_DISCONNECTED);
  }

  /**
   * entry point for debugger frontend requested user actions
   */
  public void doAction(Object action) {

    // System.out.println( "entering doAction" );
    if (action == ActionsManager.ACTION_KILL) {
      finish();
    } else if (action == ActionsManager.ACTION_CONTINUE) {
      doContinue(false);
    } else if (action == ActionsManager.ACTION_RUN_TO_CURSOR) {
      doContinue(true);
    } else if (action == ActionsManager.ACTION_START) {
      doStart();
    } else if ((action == ActionsManager.ACTION_STEP_INTO) || (action == ActionsManager.ACTION_STEP_OUT) || (action == ActionsManager.ACTION_STEP_OVER)) {
      doStep(action);
    }
  }

  /**
   * @return gurrent debugged line back
   */
  public Object getCurrentLine() {
    return _currentLine;
  }

  /*
   * @return the current number of Python threads
   */
  public int getThreadCount() {
    return _pyCookie.getDebugView().getJpyDbgContext().getThreadCount();
  }

  /*
   * @return the current number of Python Variables
   */
  public int getVariablesCount(PythonVariableTreeDataNode curNode) {
    return _pyCookie.getDebugView().getJpyDbgContext().getVariableCount(curNode);
  }

  /*
   * @return the current Python stack size
   */
  public int getStackSize() {
    return _pyCookie.getDebugView().getJpyDbgContext().getStackSize();
  }

  /*
   * @return the current String stack array
   */
  public Object[] getStack() {
    return _pyCookie.getDebugView().getJpyDbgContext().getStack();
  }

  /*
   * @return the current array of Threads
   */
  public Object[] getThreads() {
    return _pyCookie.getDebugView().getJpyDbgContext().getThreads();
  }

  /*
   * @return the current array of Variables
   */
  public Object[] getVariables(PythonVariableTreeDataNode curNode) {
    return _pyCookie.getDebugView().getJpyDbgContext().getVariables(curNode);
  }

  /**
   * add a callback for ThreadModel refreshing strategy 
   */
  public void addThreadListChangeListener(DebuggerContextChangeListener l) {
    _pyCookie.getDebugView().getJpyDbgContext().addThreadListChangeListener(l);
  }

  public void removeThreadListChangeListener(DebuggerContextChangeListener l) {
    _pyCookie.getDebugView().getJpyDbgContext().removeThreadListChangeListener(l);
  }

  /**
   * add a callback for StackModel refreshing strategy 
   */
  public void addStackListChangeListener(DebuggerContextChangeListener l) {
    _pyCookie.getDebugView().getJpyDbgContext().addStackListChangeListener(l);
  }

  public void removeStackListChangeListener(DebuggerContextChangeListener l) {
    _pyCookie.getDebugView().getJpyDbgContext().removeStackListChangeListener(l);
  }

  /**
   * add a callback for varModel refreshing strategy 
   */
  public void addVarListChangeListener(DebuggerContextChangeListener l) {
    _pyCookie.getDebugView().getJpyDbgContext().addVarListChangeListener(l);
  }

  public void removeVarListChangeListener(DebuggerContextChangeListener l) {
    _pyCookie.getDebugView().getJpyDbgContext().removeVarListChangeListener(l);
  }

  /**
   * Populate session State change to session view
   */
  private void setState(int state) {
    if (state == _state) {
      return;
    }
    _state = state;
    sessionStateChanged();
  }

  /**
   * Adds property change listener.
   *
   * @param l new listener.
   */
  public void addPropertyChangeListener(PropertyChangeListener l) {
    _pcs.addPropertyChangeListener(l);
  }

  /**
   * Removes property change listener.
   *
   * @param l removed listener.
   */
  public void removePropertyChangeListener(PropertyChangeListener l) {
    _pcs.removePropertyChangeListener(l);
  }

  /**
   * Adds property change listener.
   *
   * @param l new listener.
   */
  public void addPropertyChangeListener(
          String propertyName,
          PropertyChangeListener l) {
    _pcs.addPropertyChangeListener(propertyName, l);
  }

  /**
   * Removes property change listener.
   *
   * @param l removed listener.
   */
  public void removePropertyChangeListener(
          String propertyName,
          PropertyChangeListener l) {
    _pcs.removePropertyChangeListener(propertyName, l);
  }

  /**
   * JpyDbg core callback entry point
   */
  public void handleDebuggerEvent(PluginEvent jpyEvt) {
    JpyDbgView dbgView = _pySession.get_dbgView();
    PythonDebugContainer cont = dbgView.getJpyDbgContext();

    switch (jpyEvt.get_type()) {

      case PluginEvent.STARTING:

        // _pyCookie.getDebugView().writeLog( "jpyEvt STARTING line=" +
        // jpyEvt.get_line() );
        setState(STATE_STARTING);

        break;

      case PluginEvent.ENDING:

        // _pyCookie.getDebugView().writeLog( "jpyEvt ENDING line=" +
        // jpyEvt.get_line() );
        finish();
        setState(STATE_DISCONNECTED);

        break;

      case PluginEvent.ENTERCALL:
        // _pyCookie.getDebugView().writeLog( "jpyEvt ENTERCALL line=" +
        // jpyEvt.get_line() );

        break;

      case PluginEvent.LEAVECALL:
        // _pyCookie.getDebugView().writeLog( "jpyEvt LEAVECALL line=" +
        // jpyEvt.get_line() );

        break;

      case PluginEvent.NEWLINE:
        // _pyCookie.getDebugView().writeLog( "jpyEvt NEWLINE line=" +
        // jpyEvt.get_line() );

        // disable old position in source
        // check if source has Changed ; if yes load it
        // enable new position in source
        Utils.markCurrent(Utils.getLine(jpyEvt));
        break;

      case PluginEvent.BUSY:
        //enterRunningState() ;
        break;

      case PluginEvent.NOTBUSY:
        // leaveRunningState() ;
        break;

      case PluginEvent.NEWSOURCE:
        // _pyCookie.getDebugView().writeLog( "jpyEvt NEWSOURCE line=" +
        // jpyEvt.get_line() );

        // initially check for breakpoints populated
        if (!_breakPointsPopulated) {
          _pyCookie.getDebugView().populateBreakPoints();
          _breakPointsPopulated = true;
        }
        Utils.markCurrent(Utils.getLine(jpyEvt));
        break;

      case PluginEvent.UNDEFINED:
        _pyCookie.getDebugView().writeLog("jpyEvt UNDEFINED line=" + jpyEvt.get_line());

        break;

    }
  }

  /**
   * Called from DebuggerAntLogger.
   */
  void taskStarted(PythonEvent event) {

    _pyCookie.getDebugView().writeLog("PythonDebugger.taskStarted : I am Starting a new Debugging Session ...");
    _pyCookie.getDebugView().writeLog("This window is an interactive debugging context aware Python Shell ");
    _pyCookie.getDebugView().writeLog("where you can enter python console commands while debugging ");
    // _pyCookie.getDebugView().shellInvite();
    // System.out.println( "taskStarted entered" );

    // pick up current session view
    _pySession = event.getSession();

    JpyDbgView dbgView = _pySession.get_dbgView();

    // enable JpyDbg CallBack mecanism
    dbgView.setDebuggerInstance(this);

    // Start JpyDbg TCPIP deamon now
    dbgView.startJpyDbg(event);
  }

  /**
   * provide DebuggerSessionState back to the debugger Session view 
   */
  public String getDebuggerState(SessionsModel sModel) {
    if (_sModel == null) {
      _sModel = sModel;
    }
    System.out.println("cuSTate=" + _STATES_[_state]);
    return (_STATES_[_state]);
  }

  /**
   * Notify Session's view model of a debugger state change
   */
  private void sessionStateChanged() {
    if (_sModel != null) {
      _sModel.populateNewSessionState(this);
    }
  }

  /** lookup for PythonSession instance */
  public static PythonDebugger map(Session node) {
    return node.lookupFirst(null, PythonDebugger.class);
  }
}
