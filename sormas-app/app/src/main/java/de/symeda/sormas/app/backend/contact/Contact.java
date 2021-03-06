/*
 * SORMAS® - Surveillance Outbreak Response Management & Analysis System
 * Copyright © 2016-2018 Helmholtz-Zentrum für Infektionsforschung GmbH (HZI)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.symeda.sormas.app.backend.contact;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import de.symeda.sormas.api.Disease;
import de.symeda.sormas.api.contact.ContactClassification;
import de.symeda.sormas.api.contact.ContactProximity;
import de.symeda.sormas.api.contact.ContactRelation;
import de.symeda.sormas.api.contact.ContactStatus;
import de.symeda.sormas.api.contact.FollowUpStatus;
import de.symeda.sormas.api.contact.QuarantineType;
import de.symeda.sormas.api.utils.DataHelper;
import de.symeda.sormas.api.utils.YesNoUnknown;
import de.symeda.sormas.app.backend.caze.Case;
import de.symeda.sormas.app.backend.common.AbstractDomainObject;
import de.symeda.sormas.app.backend.person.Person;
import de.symeda.sormas.app.backend.region.District;
import de.symeda.sormas.app.backend.region.Region;
import de.symeda.sormas.app.backend.user.User;

@Entity(name=Contact.TABLE_NAME)
@DatabaseTable(tableName = Contact.TABLE_NAME)
public class Contact extends AbstractDomainObject {

	private static final long serialVersionUID = -7799607075875188799L;

	public static final String TABLE_NAME = "contacts";
	public static final String I18N_PREFIX = "Contact";

	public static final String PERSON = "person_id";
	public static final String CASE_UUID = "caseUuid";
	public static final String CASE_DISEASE = "caseDisease";
	public static final String REPORT_DATE_TIME = "reportDateTime";
	public static final String REPORTING_USER = "reportingUser";
	public static final String LAST_CONTACT_DATE = "lastContactDate";
	public static final String CONTACT_PROXIMITY = "contactProximity";
	public static final String CONTACT_CLASSIFICATION = "contactClassification";
	public static final String FOLLOW_UP_STATUS = "followUpStatus";
	public static final String FOLLOW_UP_COMMENT = "followUpComment";
	public static final String FOLLOW_UP_UNTIL = "followUpUntil";
	public static final String CONTACT_OFFICER = "contactOfficer";
	public static final String DESCRIPTION = "description";
	public static final String RELATION_TO_CASE = "relationToCase";
	public static final String RELATION_DESCRIPTION = "relationDescription";
	public static final String REPORT_LAT = "reportLat";
	public static final String REPORT_LON = "reportLon";
	public static final String REPORT_LAT_LON_ACCURACY = "reportLatLonAccuracy";

	@DatabaseField(dataType = DataType.DATE_LONG, canBeNull = true)
	private Date reportDateTime;
	@DatabaseField(foreign = true, foreignAutoRefresh = true)
	private User reportingUser;
	@DatabaseField
	private Double reportLat;
	@DatabaseField
	private Double reportLon;
	@DatabaseField
	private Float reportLatLonAccuracy;

	@DatabaseField(foreign = true, foreignAutoRefresh = true)
	private Region region;
	@DatabaseField(foreign = true, foreignAutoRefresh = true)
	private District district;
	@DatabaseField(foreign = true, foreignAutoRefresh=true, canBeNull = false, maxForeignAutoRefreshLevel = 3)
	private Person person;
	@DatabaseField
	private String caseUuid;
	@Enumerated(EnumType.STRING)
	private Disease caseDisease;
	@DatabaseField(dataType = DataType.DATE_LONG, canBeNull = true)
	private Date lastContactDate;
	@Enumerated(EnumType.STRING)
	private ContactProximity contactProximity;
	@Enumerated(EnumType.STRING)
	private ContactClassification contactClassification;
	@Enumerated(EnumType.STRING)
	private ContactStatus contactStatus;
	@Enumerated(EnumType.STRING)
	private FollowUpStatus followUpStatus;
	@Column(length=512)
	private String followUpComment;
	@DatabaseField(dataType = DataType.DATE_LONG, canBeNull = true)
	private Date followUpUntil;
	@DatabaseField(foreign = true, foreignAutoRefresh = true)
	private User contactOfficer;
	@Column(length=512)
	private String description;
	@Enumerated(EnumType.STRING)
	private ContactRelation relationToCase;
	@Column(length = 512)
	private String relationDescription;
	@Column(length = 255)
	private String externalID;

	@DatabaseField
	private String resultingCaseUuid;
	@DatabaseField(foreign = true, foreignAutoRefresh = true)
	private User resultingCaseUser;

	@DatabaseField
	private boolean highPriority;
	@Enumerated(EnumType.STRING)
	private YesNoUnknown immunosuppressiveTherapyBasicDisease;
	@Column(length = 512)
	private String immunosuppressiveTherapyBasicDiseaseDetails;
	@Enumerated(EnumType.STRING)
	private YesNoUnknown careForPeopleOver60;

	@Enumerated(EnumType.STRING)
	private QuarantineType quarantine;
	@DatabaseField(dataType = DataType.DATE_LONG, canBeNull = true)
	private Date quarantineFrom;
	@DatabaseField(dataType = DataType.DATE_LONG, canBeNull = true)
	private Date quarantineTo;

	public Person getPerson() {
		return person;
	}
	public void setPerson(Person person) {
		this.person = person;
	}
	
	public Date getReportDateTime() {
		return reportDateTime;
	}
	public void setReportDateTime(Date reportDateTime) {
		this.reportDateTime = reportDateTime;
	}
	
	public User getReportingUser() {
		return reportingUser;
	}
	public void setReportingUser(User reportingUser) {
		this.reportingUser = reportingUser;
	}
	
	public Date getLastContactDate() {
		return lastContactDate;
	}
	public void setLastContactDate(Date lastContactDate) {
		this.lastContactDate = lastContactDate;
	}
	
	public ContactProximity getContactProximity() {
		return contactProximity;
	}
	public void setContactProximity(ContactProximity contactProximity) {
		this.contactProximity = contactProximity;
	}

	public FollowUpStatus getFollowUpStatus() {
		return followUpStatus;
	}
	public void setFollowUpStatus(FollowUpStatus followUpStatus) {
		this.followUpStatus = followUpStatus;
	}


	public String getFollowUpComment() {
		return followUpComment;
	}

	public void setFollowUpComment(String followUpComment) {
		this.followUpComment = followUpComment;
	}

	public Date getFollowUpUntil() {
		return followUpUntil;
	}
	public void setFollowUpUntil(Date followUpUntil) {
		this.followUpUntil = followUpUntil;
	}

	public ContactClassification getContactClassification() {
		return contactClassification;
	}
	public void setContactClassification(ContactClassification contactClassification) {
		this.contactClassification = contactClassification;
	}

	public ContactStatus getContactStatus() {
		return contactStatus;
	}
	public void setContactStatus(ContactStatus contactStatus) {
		this.contactStatus = contactStatus;
	}

	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}

	public User getContactOfficer() {
		return contactOfficer;
	}
	public void setContactOfficer(User contactOfficer) {
		this.contactOfficer = contactOfficer;
	}

	public ContactRelation getRelationToCase() {
		return relationToCase;
	}
	public void setRelationToCase(ContactRelation relationToCase) {
		this.relationToCase = relationToCase;
	}

	public String getRelationDescription() {
		return relationDescription;
	}
	public void setRelationDescription(String relationDescription) {
		this.relationDescription = relationDescription;
	}

	public Double getReportLat() {
		return reportLat;
	}

	public void setReportLat(Double reportLat) {
		this.reportLat = reportLat;
	}

	public Double getReportLon() {
		return reportLon;
	}

	public void setReportLon(Double reportLon) {
		this.reportLon = reportLon;
	}

	@Override
	public String toString() {
		return super.toString() + " " + (getPerson() != null ? getPerson().toString() : "") + " (" + DataHelper.getShortUuid(getUuid()) + ")";
	}

	@Override
	public boolean isModifiedOrChildModified() {
		boolean modified = super.isModifiedOrChildModified();
		return person.isModifiedOrChildModified() || modified;
	}

	@Override
	public boolean isUnreadOrChildUnread() {
		boolean unread = super.isUnreadOrChildUnread();
		return person.isUnreadOrChildUnread() || unread;
	}

	@Override
	public String getI18nPrefix() {
		return I18N_PREFIX;
	}

	public Float getReportLatLonAccuracy() {
		return reportLatLonAccuracy;
	}

	public void setReportLatLonAccuracy(Float reportLatLonAccuracy) {
		this.reportLatLonAccuracy = reportLatLonAccuracy;
	}

	public String getResultingCaseUuid() {
		return resultingCaseUuid;
	}

	public void setResultingCaseUuid(String resultingCaseUuid) {
		this.resultingCaseUuid = resultingCaseUuid;
	}

	public User getResultingCaseUser() {
		return resultingCaseUser;
	}

	public void setResultingCaseUser(User resultingCaseUser) {
		this.resultingCaseUser = resultingCaseUser;
	}

	public String getCaseUuid() {
		return caseUuid;
	}

	public void setCaseUuid(String caseUuid) {
		this.caseUuid = caseUuid;
	}

	public Disease getCaseDisease() {
		return caseDisease;
	}

	public void setCaseDisease(Disease caseDisease) {
		this.caseDisease = caseDisease;
	}

	public String getExternalID() {
		return externalID;
	}

	public void setExternalID(String externalID) {
		this.externalID = externalID;
	}

	public Region getRegion() {
		return region;
	}

	public void setRegion(Region region) {
		this.region = region;
	}

	public District getDistrict() {
		return district;
	}

	public void setDistrict(District district) {
		this.district = district;
	}

	public boolean isHighPriority() {
		return highPriority;
	}

	public void setHighPriority(boolean highPriority) {
		this.highPriority = highPriority;
	}

	public YesNoUnknown getImmunosuppressiveTherapyBasicDisease() {
		return immunosuppressiveTherapyBasicDisease;
	}

	public void setImmunosuppressiveTherapyBasicDisease(YesNoUnknown immunosuppressiveTherapyBasicDisease) {
		this.immunosuppressiveTherapyBasicDisease = immunosuppressiveTherapyBasicDisease;
	}

	public String getImmunosuppressiveTherapyBasicDiseaseDetails() {
		return immunosuppressiveTherapyBasicDiseaseDetails;
	}

	public void setImmunosuppressiveTherapyBasicDiseaseDetails(String immunosuppressiveTherapyBasicDiseaseDetails) {
		this.immunosuppressiveTherapyBasicDiseaseDetails = immunosuppressiveTherapyBasicDiseaseDetails;
	}

	public YesNoUnknown getCareForPeopleOver60() {
		return careForPeopleOver60;
	}

	public void setCareForPeopleOver60(YesNoUnknown careForPeopleOver60) {
		this.careForPeopleOver60 = careForPeopleOver60;
	}

	public QuarantineType getQuarantine() {
		return quarantine;
	}

	public void setQuarantine(QuarantineType quarantine) {
		this.quarantine = quarantine;
	}

	public Date getQuarantineFrom() {
		return quarantineFrom;
	}

	public void setQuarantineFrom(Date quarantineFrom) {
		this.quarantineFrom = quarantineFrom;
	}

	public Date getQuarantineTo() {
		return quarantineTo;
	}

	public void setQuarantineTo(Date quarantineTo) {
		this.quarantineTo = quarantineTo;
	}
}
