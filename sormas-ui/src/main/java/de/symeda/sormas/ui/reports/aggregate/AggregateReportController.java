package de.symeda.sormas.ui.reports.aggregate;

import com.vaadin.navigator.View;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;

import de.symeda.sormas.api.i18n.I18nProperties;
import de.symeda.sormas.api.i18n.Strings;
import de.symeda.sormas.api.user.UserRight;
import de.symeda.sormas.ui.SormasUI;
import de.symeda.sormas.ui.utils.VaadinUiUtil;

/**
 * @author Christopher Riedel
 */
public class AggregateReportController {


	public AggregateReportController() {
	}

	public void create() {
		Window window = VaadinUiUtil.createPopupWindow();
		AggregateReportsCreateLayout createLayout = new AggregateReportsCreateLayout(window,
				UserRight.AGGREGATE_REPORT_CREATE, () -> refreshView());
		window.setHeight(90, Unit.PERCENTAGE);
		window.setWidth(createLayout.getWidth() + 64 + 20, Unit.PIXELS);
		window.setCaption(I18nProperties.getString(Strings.headingCreateNewAggregateReport));
		window.setContent(createLayout);
		UI.getCurrent().addWindow(window);
	}

	private void refreshView() {
		View currentView = SormasUI.get().getNavigator().getCurrentView();
		if (currentView instanceof AggregateReportsView) {
			// force refresh, because view didn't change
			((AggregateReportsView) currentView).enter(null);
		}
	}
}
