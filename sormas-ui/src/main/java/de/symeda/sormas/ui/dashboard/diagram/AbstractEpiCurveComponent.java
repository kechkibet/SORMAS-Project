package de.symeda.sormas.ui.dashboard.diagram;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.vaadin.hene.popupbutton.PopupButton;

import com.vaadin.server.FontAwesome;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.VerticalLayout;

import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.ui.dashboard.DashboardDataProvider;
import de.symeda.sormas.ui.highcharts.HighChart;
import de.symeda.sormas.ui.utils.CssStyles;

@SuppressWarnings("serial")
public abstract class AbstractEpiCurveComponent extends VerticalLayout {

	// Components
	protected final DashboardDataProvider dashboardDataProvider;
	protected final HighChart epiCurveChart;
	protected Label epiCurveLabel;

	// Others
	protected EpiCurveGrouping epiCurveGrouping;
	private boolean showMinimumEntries;
	private ClickListener externalExpandButtonListener;
	private ClickListener externalCollapseButtonListener;

	public AbstractEpiCurveComponent(DashboardDataProvider dashboardDataProvider) {
		this.dashboardDataProvider = dashboardDataProvider;
		epiCurveChart = new HighChart();
		epiCurveChart.setSizeFull();
		epiCurveGrouping = EpiCurveGrouping.WEEK;
		showMinimumEntries = true;
		this.setMargin(true);

		addComponent(createHeader());
		addComponent(epiCurveChart);
		setExpandRatio(epiCurveChart, 1);

		clearAndFillEpiCurveChart();
	}

	public void setExpandListener(ClickListener listener) {
		externalExpandButtonListener = listener;
	}

	public void setCollapseListener(ClickListener listener) {
		externalCollapseButtonListener = listener;
	}

	private HorizontalLayout createHeader() {
		HorizontalLayout epiCurveHeaderLayout = new HorizontalLayout();
		epiCurveHeaderLayout.setWidth(100, Unit.PERCENTAGE);
		epiCurveHeaderLayout.setSpacing(true);
		CssStyles.style(epiCurveHeaderLayout, CssStyles.VSPACE_4);

		epiCurveLabel = new Label("Epidemiological Curve");
		epiCurveLabel.setSizeUndefined();
		CssStyles.style(epiCurveLabel, CssStyles.H2, CssStyles.VSPACE_4, CssStyles.VSPACE_TOP_NONE);
		
		epiCurveHeaderLayout.addComponent(epiCurveLabel);
		epiCurveHeaderLayout.setComponentAlignment(epiCurveLabel, Alignment.BOTTOM_LEFT);
		epiCurveHeaderLayout.setExpandRatio(epiCurveLabel, 1);

		// Grouping
		PopupButton groupingDropdown = new PopupButton("Grouping");
		CssStyles.style(groupingDropdown, CssStyles.BUTTON_SUBTLE);
		{
			VerticalLayout groupingLayout = new VerticalLayout();
			groupingLayout.setMargin(true);
			groupingLayout.setSizeUndefined();
			groupingDropdown.setContent(groupingLayout);

			// Grouping option group
			OptionGroup groupingSelect = new OptionGroup();
			groupingSelect.setWidth(100, Unit.PERCENTAGE);
			groupingSelect.addItems((Object[]) EpiCurveGrouping.values());
			groupingSelect.setValue(epiCurveGrouping);
			groupingSelect.addValueChangeListener(e -> {
				epiCurveGrouping = (EpiCurveGrouping) e.getProperty().getValue();
				clearAndFillEpiCurveChart();
			});
			groupingLayout.addComponent(groupingSelect);

			// "Always show at least 7 entries" checkbox
			CheckBox minimumEntriesCheckbox = new CheckBox("Always show at least 7 entries");
			CssStyles.style(minimumEntriesCheckbox, CssStyles.VSPACE_NONE);
			minimumEntriesCheckbox.setValue(showMinimumEntries);
			minimumEntriesCheckbox.addValueChangeListener(e -> {
				showMinimumEntries = (boolean) e.getProperty().getValue();
				clearAndFillEpiCurveChart();
			});
			groupingLayout.addComponent(minimumEntriesCheckbox);

			groupingDropdown.setContent(groupingLayout);
		}
		epiCurveHeaderLayout.addComponent(groupingDropdown);
		epiCurveHeaderLayout.setComponentAlignment(groupingDropdown, Alignment.MIDDLE_RIGHT);

		// Epi curve mode
		AbstractComponent epiCurveModeSelector = createEpiCurveModeSelector();
		epiCurveHeaderLayout.addComponent(epiCurveModeSelector);
		epiCurveHeaderLayout.setComponentAlignment(epiCurveModeSelector, Alignment.MIDDLE_RIGHT);

		// "Expand" and "Collapse" buttons
		Button expandEpiCurveButton = new Button("", FontAwesome.EXPAND);
		CssStyles.style(expandEpiCurveButton, CssStyles.BUTTON_SUBTLE);
		expandEpiCurveButton.addStyleName(CssStyles.VSPACE_NONE);   
		Button collapseEpiCurveButton = new Button("", FontAwesome.COMPRESS);
		CssStyles.style(collapseEpiCurveButton, CssStyles.BUTTON_SUBTLE);
		collapseEpiCurveButton.addStyleName(CssStyles.VSPACE_NONE);

		expandEpiCurveButton.addClickListener(e -> {
			externalExpandButtonListener.buttonClick(e);
			epiCurveHeaderLayout.removeComponent(expandEpiCurveButton);
			epiCurveHeaderLayout.addComponent(collapseEpiCurveButton);
			epiCurveHeaderLayout.setComponentAlignment(collapseEpiCurveButton, Alignment.MIDDLE_RIGHT);
		});
		collapseEpiCurveButton.addClickListener(e -> {
			externalCollapseButtonListener.buttonClick(e);
			epiCurveHeaderLayout.removeComponent(collapseEpiCurveButton);
			epiCurveHeaderLayout.addComponent(expandEpiCurveButton);
			epiCurveHeaderLayout.setComponentAlignment(expandEpiCurveButton, Alignment.MIDDLE_RIGHT);
		});
		epiCurveHeaderLayout.addComponent(expandEpiCurveButton);
		epiCurveHeaderLayout.setComponentAlignment(expandEpiCurveButton, Alignment.MIDDLE_RIGHT);

		return epiCurveHeaderLayout;
	}
	
	protected abstract AbstractComponent createEpiCurveModeSelector();
	public abstract void clearAndFillEpiCurveChart();

	/**
	 * Builds a list that contains an object for each day, week or month between the from and to dates. 
	 * Additional previous days, weeks or months might be added when showMinimumEntries is true.
	 */
	protected List<Date> buildListOfFilteredDates() {
		List<Date> filteredDates = new ArrayList<>();
		Date fromDate = DateHelper.getStartOfDay(dashboardDataProvider.getFromDate());
		Date toDate = DateHelper.getEndOfDay(dashboardDataProvider.getToDate());
		Date currentDate;

		if (epiCurveGrouping == EpiCurveGrouping.DAY) {
			if (!showMinimumEntries || DateHelper.getDaysBetween(fromDate, toDate) >= 7) {
				currentDate = fromDate;
			} else {
				currentDate = DateHelper.subtractDays(toDate, 6);
			}
			while (!currentDate.after(toDate)) {
				filteredDates.add(currentDate);
				currentDate = DateHelper.addDays(currentDate, 1);
			}
		} else if (epiCurveGrouping == EpiCurveGrouping.WEEK) {
			if (!showMinimumEntries || DateHelper.getWeeksBetween(fromDate, toDate) >= 7) {
				currentDate = fromDate;
			} else {
				currentDate = DateHelper.subtractWeeks(toDate, 6);
			}
			while (!currentDate.after(toDate)) {
				filteredDates.add(currentDate);
				currentDate = DateHelper.addWeeks(currentDate, 1);
			}
		} else if (epiCurveGrouping == EpiCurveGrouping.MONTH) {
			if (!showMinimumEntries || DateHelper.getMonthsBetween(fromDate, toDate) >= 7) {
				currentDate = fromDate;
			} else {
				currentDate = DateHelper.subtractMonths(toDate, 6);
			}
			while (!currentDate.after(toDate)) {
				filteredDates.add(currentDate);
				currentDate = DateHelper.addMonths(currentDate, 1);
			}
		}

		return filteredDates;
	}

}