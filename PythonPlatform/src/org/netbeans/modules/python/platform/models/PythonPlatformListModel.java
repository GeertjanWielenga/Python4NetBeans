/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.python.platform.models;

import java.util.List;
import javax.swing.AbstractListModel;
import org.netbeans.modules.python.api.PythonPlatform;
import org.netbeans.modules.python.api.PythonPlatformManager;

/**
 *
 * @author alley
 */
public class PythonPlatformListModel extends AbstractListModel {
    private PythonPlatformManager manager = PythonPlatformManager.getInstance();
    private List<PythonPlatform> model = manager.getPlatforms();

    public int getSize() {
        return model.size();
    }

    public Object getElementAt(int index) {
        if (index >= 0 && index < model.size()) {
            return model.get(index);
        } else {
            return null;
        }
    }
    
    public void refresh(){
        model = manager.getPlatforms();
        fireContentsChanged(this, 0, model.size() -1);
    }
}
