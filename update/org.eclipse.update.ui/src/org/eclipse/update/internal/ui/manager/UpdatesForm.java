package org.eclipse.update.internal.ui.manager;

import org.eclipse.update.internal.ui.parts.*;
import org.eclipse.update.internal.ui.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;

public class UpdatesForm extends FeatureSelectionForm {
	public UpdatesForm(UpdateFormPage page) {
		super(page, IUpdateModes.UPDATE);
	}
	
public void initialize(Object modelObject) {
	setTitle("Feature Updates");
	super.initialize(modelObject);
}

}

