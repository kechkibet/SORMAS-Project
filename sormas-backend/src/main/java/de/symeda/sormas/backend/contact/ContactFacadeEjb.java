/*******************************************************************************
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
 *******************************************************************************/
package de.symeda.sormas.backend.contact;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.symeda.sormas.api.Disease;
import de.symeda.sormas.api.caze.MapCaseDto;
import de.symeda.sormas.api.contact.ContactClassification;
import de.symeda.sormas.api.contact.ContactCriteria;
import de.symeda.sormas.api.contact.ContactDto;
import de.symeda.sormas.api.contact.ContactExportDto;
import de.symeda.sormas.api.contact.ContactFacade;
import de.symeda.sormas.api.contact.ContactFollowUpDto;
import de.symeda.sormas.api.contact.ContactIndexDto;
import de.symeda.sormas.api.contact.ContactReferenceDto;
import de.symeda.sormas.api.contact.ContactStatus;
import de.symeda.sormas.api.contact.DashboardContactDto;
import de.symeda.sormas.api.contact.FollowUpStatus;
import de.symeda.sormas.api.contact.MapContactDto;
import de.symeda.sormas.api.i18n.I18nProperties;
import de.symeda.sormas.api.i18n.Validations;
import de.symeda.sormas.api.region.DistrictReferenceDto;
import de.symeda.sormas.api.region.RegionReferenceDto;
import de.symeda.sormas.api.task.TaskContext;
import de.symeda.sormas.api.task.TaskCriteria;
import de.symeda.sormas.api.task.TaskPriority;
import de.symeda.sormas.api.task.TaskStatus;
import de.symeda.sormas.api.task.TaskType;
import de.symeda.sormas.api.user.UserRight;
import de.symeda.sormas.api.user.UserRole;
import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.api.utils.SortProperty;
import de.symeda.sormas.api.utils.ValidationRuntimeException;
import de.symeda.sormas.api.utils.YesNoUnknown;
import de.symeda.sormas.api.visit.VisitResult;
import de.symeda.sormas.api.visit.VisitStatus;
import de.symeda.sormas.backend.caze.Case;
import de.symeda.sormas.backend.caze.CaseFacadeEjb;
import de.symeda.sormas.backend.caze.CaseFacadeEjb.CaseFacadeEjbLocal;
import de.symeda.sormas.backend.caze.CaseService;
import de.symeda.sormas.backend.common.AbstractAdoService;
import de.symeda.sormas.backend.common.AbstractDomainObject;
import de.symeda.sormas.backend.facility.Facility;
import de.symeda.sormas.backend.location.LocationService;
import de.symeda.sormas.backend.person.Person;
import de.symeda.sormas.backend.person.PersonFacadeEjb;
import de.symeda.sormas.backend.person.PersonService;
import de.symeda.sormas.backend.region.District;
import de.symeda.sormas.backend.region.DistrictFacadeEjb;
import de.symeda.sormas.backend.region.DistrictService;
import de.symeda.sormas.backend.region.Region;
import de.symeda.sormas.backend.region.RegionFacadeEjb;
import de.symeda.sormas.backend.region.RegionService;
import de.symeda.sormas.backend.symptoms.Symptoms;
import de.symeda.sormas.backend.task.Task;
import de.symeda.sormas.backend.task.TaskService;
import de.symeda.sormas.backend.user.User;
import de.symeda.sormas.backend.user.UserFacadeEjb;
import de.symeda.sormas.backend.user.UserRoleConfigFacadeEjb.UserRoleConfigFacadeEjbLocal;
import de.symeda.sormas.backend.user.UserService;
import de.symeda.sormas.backend.util.DateHelper8;
import de.symeda.sormas.backend.util.DtoHelper;
import de.symeda.sormas.backend.util.ModelConstants;
import de.symeda.sormas.backend.visit.Visit;
import de.symeda.sormas.backend.visit.VisitService;

@Stateless(name = "ContactFacade")
public class ContactFacadeEjb implements ContactFacade {

	@PersistenceContext(unitName = ModelConstants.PERSISTENCE_UNIT_NAME)
	protected EntityManager em;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@EJB
	private ContactService contactService;	
	@EJB
	private CaseService caseService;
	@EJB
	private PersonService personService;
	@EJB
	private UserService userService;
	@EJB
	private VisitService visitService;
	@EJB
	private TaskService taskService;
	@EJB
	private RegionService regionService;
	@EJB
	private DistrictService districtService;
	@EJB
	private LocationService locationService;
	@EJB
	private CaseFacadeEjbLocal caseFacade;
	@EJB
	private UserRoleConfigFacadeEjbLocal userRoleConfigFacade;
	
	@Override
	public List<String> getAllActiveUuids(String userUuid) {
		User user = userService.getByUuid(userUuid);

		if (user == null) {
			return Collections.emptyList();
		}

		return contactService.getAllActiveUuids(user);
	}	

	@Override
	public List<ContactDto> getAllActiveContactsAfter(Date date, String userUuid) {
		User user = userService.getByUuid(userUuid);

		if (user == null) {
			return Collections.emptyList();
		}

		return contactService.getAllActiveContactsAfter(date, user).stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public List<ContactDto> getByUuids(List<String> uuids) {
		return contactService.getByUuids(uuids)
				.stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public List<String> getDeletedUuidsSince(String userUuid, Date since) {
		User user = userService.getByUuid(userUuid);

		if (user == null) {
			return Collections.emptyList();
		}

		return contactService.getDeletedUuidsSince(user, since);
	}

	@Override
	public ContactDto getContactByUuid(String uuid) {
		return toDto(contactService.getByUuid(uuid));
	}

	@Override
	public ContactReferenceDto getReferenceByUuid(String uuid) {
		return toReferenceDto(contactService.getByUuid(uuid));
	}

	@Override
	public ContactDto saveContact(ContactDto dto) {
		return saveContact(dto, true);
	}
	
	public ContactDto saveContact(ContactDto dto, boolean handleChanges) {
		Contact entity = fromDto(dto);

		// taking this out because it may lead to server problems
		// case disease can change over time and there is currently no mechanism that would delete all related contacts
		// in this case the best solution is to only keep this hidden from the UI and still allow it in the backend
//		if (!DiseaseHelper.hasContactFollowUp(entity.getCaze().getDisease(), entity.getCaze().getPlagueType())) {
//			throw new UnsupportedOperationException("Contact creation is not allowed for diseases that don't have contact follow-up.");
//		}
		
		contactService.ensurePersisted(entity);
		
		if (handleChanges) {

			contactService.updateFollowUpUntilAndStatus(entity);
			contactService.udpateContactStatus(entity);
			
			caseFacade.onCaseChanged(CaseFacadeEjbLocal.toDto(entity.getCaze()), entity.getCaze());
		}
		
		return toDto(entity);
	}
	
	@Override
	public List<MapContactDto> getContactsForMap(RegionReferenceDto regionRef, DistrictReferenceDto districtRef, Disease disease, Date fromDate, Date toDate, String userUuid, List<MapCaseDto> mapCaseDtos) {
		User user = userService.getByUuid(userUuid);
		Region region = regionService.getByReferenceDto(regionRef);
		District district = districtService.getByReferenceDto(districtRef);
		List<String> caseUuids = new ArrayList<>();
		for (MapCaseDto mapCaseDto : mapCaseDtos) {
			caseUuids.add(mapCaseDto.getUuid());
		}

		if (user == null) {
			return Collections.emptyList();
		}
		
		return contactService.getContactsForMap(region, district, disease, fromDate, toDate, user, caseUuids);
	}
	
	@Override
	public void deleteContact(String contactUuid, String userUuid) {
		User user = userService.getByUuid(userUuid);
		if (!userRoleConfigFacade.getEffectiveUserRights(user.getUserRoles().toArray(new UserRole[user.getUserRoles().size()])).contains(UserRight.CONTACT_DELETE)) {
			throw new UnsupportedOperationException("User " + userUuid + " is not allowed to delete contacts.");
		}
		
		Contact contact = contactService.getByUuid(contactUuid);
		contactService.delete(contact);
		
		caseFacade.onCaseChanged(CaseFacadeEjbLocal.toDto(contact.getCaze()), contact.getCaze());
	}
	
	@Override
	public List<ContactExportDto> getExportList(String userUuid, ContactCriteria contactCriteria, int first, int max) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ContactExportDto> cq = cb.createQuery(ContactExportDto.class);
		Root<Contact> contact = cq.from(Contact.class);
		Join<Contact, Case> contactCase = contact.join(Contact.CAZE, JoinType.LEFT);
		Join<Contact, Person> contactPerson = contact.join(Contact.PERSON, JoinType.LEFT);
		Join<Person, Facility> occupationFacility = contactPerson.join(Person.OCCUPATION_FACILITY, JoinType.LEFT);

		cq.multiselect(
				contact.get(Contact.ID),
				contactPerson.get(Person.ID),
				contact.get(Contact.UUID),
				contactCase.get(Case.UUID),
				contactCase.get(Case.CASE_CLASSIFICATION),
				contactCase.get(Case.DISEASE),
				contactCase.get(Case.DISEASE_DETAILS),
				contact.get(Contact.CONTACT_CLASSIFICATION),
				contact.get(Contact.LAST_CONTACT_DATE),
				contactPerson.get(Person.FIRST_NAME),
				contactPerson.get(Person.LAST_NAME),
				contactPerson.get(Person.SEX),
				contactPerson.get(Person.APPROXIMATE_AGE),
				contactPerson.get(Person.APPROXIMATE_AGE_TYPE),
				contact.get(Contact.REPORT_DATE_TIME),
				contact.get(Contact.CONTACT_PROXIMITY),
				contact.get(Contact.CONTACT_STATUS),
				contact.get(Contact.FOLLOW_UP_STATUS),
				contact.get(Contact.FOLLOW_UP_UNTIL),
				contactPerson.get(Person.PRESENT_CONDITION),
				contactPerson.get(Person.DEATH_DATE),
				contactPerson.get(Person.PHONE),
				contactPerson.get(Person.PHONE_OWNER),
				contactPerson.get(Person.OCCUPATION_TYPE),
				contactPerson.get(Person.OCCUPATION_DETAILS),
				occupationFacility.get(Facility.NAME),
				occupationFacility.get(Facility.UUID),
				contactPerson.get(Person.OCCUPATION_FACILITY_DETAILS));
		
		Predicate filter = null;

		// Only use user filter if no restricting case is specified
		if (userUuid != null 
				&& (contactCriteria == null || contactCriteria.getCaze() == null)) {
			User user = userService.getByUuid(userUuid);
			filter = contactService.createUserFilter(cb, cq, contact, user);
		}
		
		if (contactCriteria != null) {
			Predicate criteriaFilter = contactService.buildCriteriaFilter(contactCriteria, cb, contact);
			filter = AbstractAdoService.and(cb, filter, criteriaFilter);
		}

		if (filter != null) {
			cq.where(filter);
		}
		
		cq.orderBy(cb.desc(contact.get(Contact.REPORT_DATE_TIME)));
		
		List<ContactExportDto> resultList = em.createQuery(cq).setFirstResult(first).setMaxResults(max).getResultList();

		for (ContactExportDto exportDto : resultList) {
			// TODO: Speed up this code, e.g. by persisting address as a String in the database
			exportDto.setAddress(personService.getAddressByPersonId(exportDto.getPersonId()).toString());
			exportDto.setNumberOfVisits(visitService.getVisitCountByContactId(exportDto.getPersonId(), 
					exportDto.getLastContactDate(), exportDto.getReportDate(), exportDto.getFollowUpUntil(), exportDto.getInternalDisease()));
			Visit lastCooperativeVisit = visitService.getLastVisitByContactId(exportDto.getPersonId(), 
					exportDto.getLastContactDate(), exportDto.getReportDate(), exportDto.getFollowUpUntil(), exportDto.getInternalDisease(), VisitStatus.COOPERATIVE);
			if (lastCooperativeVisit != null) {
				exportDto.setLastCooperativeVisitSymptomatic(lastCooperativeVisit.getSymptoms().getSymptomatic() ? YesNoUnknown.YES : YesNoUnknown.NO);
				exportDto.setLastCooperativeVisitDate(lastCooperativeVisit.getVisitDateTime());
				exportDto.setLastCooperativeVisitSymptoms(lastCooperativeVisit.getSymptoms().toHumanString(true));
			}
		}
		
		return resultList;
	}
	
	@Override
	public long count(String userUuid, ContactCriteria contactCriteria) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Contact> root = cq.from(Contact.class);
		
		Predicate filter = null;		
		// Only use user filter if no restricting case is specified
		if (userUuid != null 
				&& (contactCriteria == null || contactCriteria.getCaze() == null)) {
			User user = userService.getByUuid(userUuid);
			filter = contactService.createUserFilter(cb, cq, root, user);
		}
		
		if (contactCriteria != null) {
			Predicate criteriaFilter = contactService.buildCriteriaFilter(contactCriteria, cb, root);
			filter = AbstractAdoService.and(cb, filter, criteriaFilter);
		}

		if (filter != null) {
			cq.where(filter);
		}
		
		cq.select(cb.count(root));
		return em.createQuery(cq).getSingleResult();
	}
	
	@Override
	public List<ContactFollowUpDto> getContactFollowUpList(String userUuid, ContactCriteria contactCriteria, Date referenceDate, Integer first, Integer max, List<SortProperty> sortProperties) {
		Date end = DateHelper.getEndOfDay(referenceDate);
		Date start = DateHelper.getStartOfDay(DateHelper.subtractDays(end, 7));
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ContactFollowUpDto> cq = cb.createQuery(ContactFollowUpDto.class);
		Root<Contact> contact = cq.from(Contact.class);
		Join<Contact, Person> contactPerson = contact.join(Contact.PERSON, JoinType.LEFT);
		Join<Contact, User> contactOfficer = contact.join(Contact.CONTACT_OFFICER, JoinType.LEFT);
		Join<Contact, Case> contactCase = contact.join(Contact.CAZE, JoinType.LEFT);
		
		cq.multiselect(contact.get(Contact.UUID), contactPerson.get(Person.UUID), contactPerson.get(Person.FIRST_NAME), contactPerson.get(Person.LAST_NAME),
				contactOfficer.get(User.UUID), contactOfficer.get(User.FIRST_NAME), contactOfficer.get(User.LAST_NAME), contact.get(Contact.LAST_CONTACT_DATE),
				contact.get(Contact.REPORT_DATE_TIME), contact.get(Contact.FOLLOW_UP_UNTIL), contactCase.get(Case.DISEASE));
		
		Predicate filter = null;		
		// Only use user filter if no restricting case is specified
		if (userUuid != null && (contactCriteria == null || contactCriteria.getCaze() == null)) {
			User user = userService.getByUuid(userUuid);
			filter = contactService.createUserFilter(cb, cq, contact, user);
		}
		
		if (contactCriteria != null) {
			Predicate criteriaFilter = contactService.buildCriteriaFilter(contactCriteria, cb, contact);
			filter = AbstractAdoService.and(cb, filter, criteriaFilter);
		}

		if (filter != null) {
			cq.where(filter);
		}
		
		if (sortProperties != null && sortProperties.size() > 0) {
			List<Order> order = new ArrayList<Order>(sortProperties.size());
			for (SortProperty sortProperty : sortProperties) {
				Expression<?> expression;
				switch (sortProperty.propertyName) {
				case ContactFollowUpDto.UUID:
				case ContactFollowUpDto.LAST_CONTACT_DATE:
				case ContactFollowUpDto.REPORT_DATE_TIME:
				case ContactFollowUpDto.FOLLOW_UP_UNTIL:
					expression = contact.get(sortProperty.propertyName);
					break;
				case ContactFollowUpDto.PERSON:
					expression = contactPerson.get(Person.FIRST_NAME);
					order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
					expression = contactPerson.get(Person.LAST_NAME);
					break;
				case ContactFollowUpDto.CONTACT_OFFICER:
					expression = contactOfficer.get(User.FIRST_NAME);
					order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
					expression = contactOfficer.get(User.LAST_NAME);
					break;
				default:
					throw new IllegalArgumentException(sortProperty.propertyName);
				}
				order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
			}
			cq.orderBy(order);
		} else {
			cq.orderBy(cb.desc(contact.get(Contact.CHANGE_DATE)));
		}
		
		List<ContactFollowUpDto> resultList = em.createQuery(cq).setFirstResult(first).setMaxResults(max).getResultList();
		
		// TODO: Refactor this to two individual queries (not one query per DTO) after general contact refactoring
		for (ContactFollowUpDto followUpDto : resultList) {
			List<Object[]> followUpInfoList = null;
			CriteriaQuery<Object[]> followUpInfoCq = cb.createQuery(Object[].class);
			Root<Visit> visitRoot = followUpInfoCq.from(Visit.class);
			Join<Visit, Symptoms> symptomsJoin = visitRoot.join(Visit.SYMPTOMS, JoinType.LEFT);
			
			followUpInfoCq.multiselect(visitRoot.get(Visit.VISIT_DATE_TIME), visitRoot.get(Visit.VISIT_STATUS), 
					symptomsJoin.get(Symptoms.SYMPTOMATIC));
			
			followUpInfoCq.where(
					cb.and(
							cb.equal(visitRoot.get(Visit.PERSON).get(Person.UUID), followUpDto.getPerson().getUuid()),
							cb.equal(visitRoot.get(Visit.DISEASE), followUpDto.getDisease()),
							cb.between(visitRoot.get(Visit.VISIT_DATE_TIME), start, end)
							)
					);
			
			followUpInfoList = em.createQuery(followUpInfoCq).getResultList();
			
			for (Object[] followUpInfo : followUpInfoList) {
				int day = DateHelper.getDaysBetween(start, (Date) followUpInfo[0]);
				VisitResult result = getVisitResult((VisitStatus) followUpInfo[1], (boolean) followUpInfo[2]);
				followUpDto.getVisitResults()[day - 1] = result;
			}
		}
		
		return resultList;
	}
	
	private VisitResult getVisitResult(VisitStatus status, boolean symptomatic) {
		if (VisitStatus.UNCOOPERATIVE.equals(status)) {
			return VisitResult.UNCOOPERATIVE;
		}
		if (VisitStatus.UNAVAILABLE.equals(status)) {
			return VisitResult.UNAVAILABLE;
		}
		if (symptomatic) {
			return VisitResult.SYMPTOMATIC;
		}
		return VisitResult.NOT_SYMPTOMATIC;
	}
	
	@Override
	public List<ContactIndexDto> getIndexList(String userUuid, ContactCriteria contactCriteria, Integer first, Integer max, List<SortProperty> sortProperties) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ContactIndexDto> cq = cb.createQuery(ContactIndexDto.class);
		Root<Contact> contact = cq.from(Contact.class);
		Join<Contact, Person> contactPerson = contact.join(Contact.PERSON, JoinType.LEFT);
		Join<Contact, Case> contactCase = contact.join(Contact.CAZE, JoinType.LEFT);
		Join<Case, Person> contactCasePerson = contactCase.join(Case.PERSON, JoinType.LEFT);
		Join<Case, Region> contactCaseRegion = contactCase.join(Case.REGION, JoinType.LEFT);
		Join<Case, District> contactCaseDistrict = contactCase.join(Case.DISTRICT, JoinType.LEFT);
		Join<Case, Facility> contactCaseFacility = contactCase.join(Case.HEALTH_FACILITY, JoinType.LEFT);
		Join<Contact, User> contactOfficer = contact.join(Contact.CONTACT_OFFICER, JoinType.LEFT);
		
		cq.multiselect(contact.get(Contact.UUID), contactPerson.get(Person.UUID), contactPerson.get(Person.FIRST_NAME), contactPerson.get(Person.LAST_NAME),
				contactCase.get(Case.UUID), contactCase.get(Case.DISEASE), contactCase.get(Case.DISEASE_DETAILS), contactCasePerson.get(Person.UUID), contactCasePerson.get(Person.FIRST_NAME),
				contactCasePerson.get(Person.LAST_NAME), contactCaseRegion.get(Region.UUID), contactCaseDistrict.get(District.UUID),
				contactCaseFacility.get(Facility.UUID), contact.get(Contact.LAST_CONTACT_DATE), contact.get(Contact.CONTACT_PROXIMITY),
				contact.get(Contact.CONTACT_CLASSIFICATION), contact.get(Contact.CONTACT_STATUS), contact.get(Contact.FOLLOW_UP_STATUS), contact.get(Contact.FOLLOW_UP_UNTIL),
				contactOfficer.get(User.UUID), contact.get(Contact.REPORT_DATE_TIME));
		
		Predicate filter = null;		
		// Only use user filter if no restricting case is specified
		if (userUuid != null && (contactCriteria == null || contactCriteria.getCaze() == null)) {
			User user = userService.getByUuid(userUuid);
			filter = contactService.createUserFilter(cb, cq, contact, user);
		}
		
		if (contactCriteria != null) {
			Predicate criteriaFilter = contactService.buildCriteriaFilter(contactCriteria, cb, contact);
			filter = AbstractAdoService.and(cb, filter, criteriaFilter);
		}

		if (filter != null) {
			cq.where(filter);
		}
		
		if (sortProperties != null && sortProperties.size() > 0) {
			List<Order> order = new ArrayList<Order>(sortProperties.size());
			for (SortProperty sortProperty : sortProperties) {
				Expression<?> expression;
				switch (sortProperty.propertyName) {
				case ContactIndexDto.UUID:
				case ContactIndexDto.LAST_CONTACT_DATE:
				case ContactIndexDto.CONTACT_PROXIMITY:
				case ContactIndexDto.CONTACT_CLASSIFICATION:
				case ContactIndexDto.CONTACT_STATUS:
				case ContactIndexDto.FOLLOW_UP_STATUS:
				case ContactIndexDto.FOLLOW_UP_UNTIL:
				case ContactIndexDto.REPORT_DATE_TIME:
					expression = contact.get(sortProperty.propertyName);
					break;
				case ContactIndexDto.PERSON:
					expression = contactPerson.get(Person.FIRST_NAME);
					order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
					expression = contactPerson.get(Person.LAST_NAME);
					break;
				case ContactIndexDto.CAZE:
					expression = contactCasePerson.get(Person.FIRST_NAME);
					order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
					expression = contactCasePerson.get(Person.LAST_NAME);
					break;
				case ContactIndexDto.CASE_DISEASE:
					expression = contactCase.get(Case.DISEASE);
					break;
				case ContactIndexDto.CASE_REGION_UUID:
					expression = contactCaseRegion.get(Region.NAME);
					break;
				case ContactIndexDto.CASE_DISTRICT_UUID:
					expression = contactCaseDistrict.get(District.NAME);
					break;
				case ContactIndexDto.CASE_HEALTH_FACILITY_UUID:
					expression = contactCaseFacility.get(Facility.NAME);
					break;
				default:
					throw new IllegalArgumentException(sortProperty.propertyName);
				}
				order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
			}
			cq.orderBy(order);
		} else {
			cq.orderBy(cb.desc(contact.get(Contact.CHANGE_DATE)));
		}

		if (first != null && max != null) {
			return em.createQuery(cq).setFirstResult(first).setMaxResults(max).getResultList();
		} else {
			return em.createQuery(cq).getResultList();
		}
	}
	
	@Override
	public int[] getContactCountsByCasesForDashboard(List<String> contactUuids) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Contact> contact = cq.from(Contact.class);
		Join<Contact, Case> caseJoin = contact.join(Contact.CAZE, JoinType.LEFT);
		
		cq.where(contact.get(Contact.UUID).in(contactUuids));
		cq.select(caseJoin.get(Case.ID));
		cq.distinct(true);
		
		List<Long> caseIds = em.createQuery(cq).getResultList();
		
		CriteriaQuery<Long> cq2 = cb.createQuery(Long.class);
		Root<Contact> contact2 = cq2.from(Contact.class);
		cq2.groupBy(contact2.get(Contact.CAZE));
		
		cq2.where(contact2.get(Contact.CAZE).in(caseIds));
		cq2.select(cb.count(contact2.get(Contact.ID)));

		List<Long> caseContactCounts = em.createQuery(cq2).getResultList();

		int[] counts = new int[3];
		counts[0] = caseContactCounts.stream().min((l1, l2) -> l1.compareTo(l2)).orElse(0L).intValue();
		counts[1] = caseContactCounts.stream().max((l1, l2) -> l1.compareTo(l2)).orElse(0L).intValue();
		counts[2] =  caseContactCounts.stream().reduce(0L, (a, b) -> a + b).intValue() / caseIds.size();
		return counts;
	}
	
	@Override
	public int getNonSourceCaseCountForDashboard(List<String> caseUuids) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Contact> contact = cq.from(Contact.class);
		Join<Contact, Case> caseJoin = contact.join(Contact.RESULTING_CASE, JoinType.LEFT);
		
		cq.where(caseJoin.get(Case.UUID).in(caseUuids));
		cq.select(cb.count(caseJoin.get(Case.ID)));
		cq.distinct(true);
		
		return em.createQuery(cq).getSingleResult().intValue();
	}

	public Contact fromDto(@NotNull ContactDto source) {

		Contact target = contactService.getByUuid(source.getUuid());
		if (target == null) {
			target = new Contact();
			target.setUuid(source.getUuid());
			if (source.getCreationDate() != null) {
				target.setCreationDate(new Timestamp(source.getCreationDate().getTime()));
			}
		}
		DtoHelper.validateDto(source, target);

		target.setCaze(caseService.getByReferenceDto(source.getCaze()));
		target.setPerson(personService.getByReferenceDto(source.getPerson()));

		target.setReportingUser(userService.getByReferenceDto(source.getReportingUser()));
		if (source.getReportDateTime() != null) {
			target.setReportDateTime(source.getReportDateTime());
		} else { // make sure we do have a report date
			target.setReportDateTime(new Date());
		}

		// use only date, not time
		target.setLastContactDate(source.getLastContactDate() != null ? DateHelper8.toDate(DateHelper8.toLocalDate(source.getLastContactDate())) : null);

		target.setContactProximity(source.getContactProximity());
		target.setContactClassification(source.getContactClassification());
		target.setContactStatus(source.getContactStatus());
		target.setFollowUpStatus(source.getFollowUpStatus());
		target.setFollowUpComment(source.getFollowUpComment());
		target.setFollowUpUntil(source.getFollowUpUntil());
		target.setContactOfficer(userService.getByReferenceDto(source.getContactOfficer()));
		target.setDescription(source.getDescription());
		target.setRelationToCase(source.getRelationToCase());
		target.setRelationDescription(source.getRelationDescription());
		target.setResultingCase(caseService.getByReferenceDto(source.getResultingCase()));

		target.setReportLat(source.getReportLat());
		target.setReportLon(source.getReportLon());
		target.setReportLatLonAccuracy(source.getReportLatLonAccuracy());
		target.setExternalID(source.getExternalID());
		
		target.setRegion(regionService.getByReferenceDto(source.getRegion()));
		target.setDistrict(districtService.getByReferenceDto(source.getDistrict()));

		target.setHighPriority(source.isHighPriority());
		target.setImmunosuppressiveTherapyBasicDisease(source.getImmunosuppressiveTherapyBasicDisease());
		target.setImmunosuppressiveTherapyBasicDiseaseDetails(source.getImmunosuppressiveTherapyBasicDiseaseDetails());
		target.setCareForPeopleOver60(source.getCareForPeopleOver60());
		
		target.setQuarantine(source.getQuarantine());
		target.setQuarantineFrom(source.getQuarantineFrom());
		target.setQuarantineTo(source.getQuarantineTo());

		return target;
	}

	@Override
	public boolean isDeleted(String contactUuid) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Contact> from = cq.from(Contact.class);

		cq.where(cb.and(
				cb.isTrue(from.get(Contact.DELETED)),
				cb.equal(from.get(AbstractDomainObject.UUID), contactUuid)));
		cq.select(cb.count(from));
		long count = em.createQuery(cq).getSingleResult();
		return count > 0;
	}
	
	@Override
	public List<DashboardContactDto> getContactsForDashboard(RegionReferenceDto regionRef,
			DistrictReferenceDto districtRef, Disease disease, Date from, Date to, String userUuid) {
		Region region = regionService.getByReferenceDto(regionRef);
		District district = districtService.getByReferenceDto(districtRef);
		User user = userService.getByUuid(userUuid);
		
		return contactService.getContactsForDashboard(region, district, disease, from, to, user);
	}
	
	@Override
	public Map<ContactStatus, Long> getNewContactCountPerStatus(ContactCriteria contactCriteria, String userUuid) {
		User user = userService.getByUuid(userUuid);
		
		return contactService.getNewContactCountPerStatus(contactCriteria, user);
	}
	
	@Override
	public Map<ContactClassification, Long> getNewContactCountPerClassification(ContactCriteria contactCriteria, String userUuid) {
		User user = userService.getByUuid(userUuid);
		
		return contactService.getNewContactCountPerClassification(contactCriteria, user);
	}
	
	@Override
	public Map<FollowUpStatus, Long> getNewContactCountPerFollowUpStatus(ContactCriteria contactCriteria, String userUuid) {
		User user = userService.getByUuid(userUuid);
		
		return contactService.getNewContactCountPerFollowUpStatus(contactCriteria, user);
	}
	
	@Override
	public int getFollowUpUntilCount(ContactCriteria contactCriteria, String userUuid) {
		User user = userService.getByUuid(userUuid);
		
		return contactService.getFollowUpUntilCount(contactCriteria, user);
	}

	public static ContactReferenceDto toReferenceDto(Contact source) {
		if (source == null) {
			return null;
		}
		ContactReferenceDto target = new ContactReferenceDto(source.getUuid(), source.toString());
		return target;
	}	

	public static ContactDto toDto(Contact source) {
		if (source == null) {
			return null;
		}
		ContactDto target = new ContactDto();
		DtoHelper.fillDto(target, source);

		target.setCaze(CaseFacadeEjb.toReferenceDto(source.getCaze()));
		target.setCaseDisease(source.getCaze().getDisease());
		target.setPerson(PersonFacadeEjb.toReferenceDto(source.getPerson()));

		target.setReportingUser(UserFacadeEjb.toReferenceDto(source.getReportingUser()));
		target.setReportDateTime(source.getReportDateTime());

		target.setLastContactDate(source.getLastContactDate());
		target.setContactProximity(source.getContactProximity());
		target.setContactClassification(source.getContactClassification());
		target.setContactStatus(source.getContactStatus());
		target.setFollowUpStatus(source.getFollowUpStatus());
		target.setFollowUpComment(source.getFollowUpComment());
		target.setFollowUpUntil(source.getFollowUpUntil());
		target.setContactOfficer(UserFacadeEjb.toReferenceDto(source.getContactOfficer()));
		target.setDescription(source.getDescription());
		target.setRelationToCase(source.getRelationToCase());
		target.setRelationDescription(source.getRelationDescription());
		target.setResultingCase(CaseFacadeEjb.toReferenceDto(source.getResultingCase()));

		target.setReportLat(source.getReportLat());
		target.setReportLon(source.getReportLon());
		target.setReportLatLonAccuracy(source.getReportLatLonAccuracy());
		target.setExternalID(source.getExternalID());
		
		target.setRegion(RegionFacadeEjb.toReferenceDto(source.getRegion()));
		target.setDistrict(DistrictFacadeEjb.toReferenceDto(source.getDistrict()));

		target.setHighPriority(source.isHighPriority());
		target.setImmunosuppressiveTherapyBasicDisease(source.getImmunosuppressiveTherapyBasicDisease());
		target.setImmunosuppressiveTherapyBasicDiseaseDetails(source.getImmunosuppressiveTherapyBasicDiseaseDetails());
		target.setCareForPeopleOver60(source.getCareForPeopleOver60());
		
		target.setQuarantine(source.getQuarantine());
		target.setQuarantineFrom(source.getQuarantineFrom());
		target.setQuarantineTo(source.getQuarantineTo());

		return target;
	}

	@RolesAllowed(UserRole._SYSTEM)
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public void generateContactFollowUpTasks() {
		// get all contacts that are followed up
		LocalDateTime fromDateTime = LocalDate.now().atStartOfDay();
		LocalDateTime toDateTime = fromDateTime.plusDays(1);
		List<Contact> contacts = contactService.getFollowUpBetween(DateHelper8.toDate(fromDateTime), DateHelper8.toDate(toDateTime), null, null, null);

		for (Contact contact : contacts) {
			User assignee;
			// assign responsible user
			if (contact.getContactOfficer() != null) {
				// A. contact officer
				assignee = contact.getContactOfficer();
			} else {
				// use region where contact person lifes
				Region region = contact.getPerson().getAddress().getRegion();
				if (region == null) {
					// fallback: use region of related caze
					region = contact.getCaze().getRegion();
				}
				// B. contact supervisor
				List<User> users = userService.getAllByRegionAndUserRoles(region, UserRole.CONTACT_SUPERVISOR);
				if (!users.isEmpty()) {
					assignee = users.get(0);
				} else {
					logger.warn("Contact has not contact officer and no region - can't create follow-up task: " + contact.getUuid());
					continue;
				}
			}

			// find already existing tasks
			TaskCriteria pendingUserTaskCriteria = new TaskCriteria()
					.contact(contact.toReference())
					.taskType(TaskType.CONTACT_FOLLOW_UP)
					.assigneeUser(assignee.toReference())
					.taskStatus(TaskStatus.PENDING);
			List<Task> pendingUserTasks = taskService.findBy(pendingUserTaskCriteria);

			if (!pendingUserTasks.isEmpty()) {
				// the user still has a pending task for this contact
				continue;
			}

			TaskCriteria dayTaskCriteria = new TaskCriteria()
					.contact(contact.toReference())
					.taskType(TaskType.CONTACT_FOLLOW_UP)
					.dueDateBetween(DateHelper8.toDate(fromDateTime), DateHelper8.toDate(toDateTime));
			List<Task> dayTasks = taskService.findBy(dayTaskCriteria);

			if (!dayTasks.isEmpty()) {
				// there is already a task for the exact day
				continue;
			}

			// none found -> create the task
			Task task = taskService.buildTask(null);
			task.setTaskContext(TaskContext.CONTACT);
			task.setContact(contact);
			task.setTaskType(TaskType.CONTACT_FOLLOW_UP);
			task.setSuggestedStart(DateHelper8.toDate(fromDateTime));
			task.setDueDate(DateHelper8.toDate(toDateTime.minusMinutes(1)));
			task.setAssigneeUser(assignee);
			
			if (contact.isHighPriority()) {
				task.setPriority(TaskPriority.HIGH);
			}
			
			taskService.ensurePersisted(task);
		}
	}

	@Override
	public void validate(ContactDto contact) throws ValidationRuntimeException {
		// Check whether any required field that does not have a not null constraint in
		// the database is empty
		if (contact.getReportDateTime() == null) {
			throw new ValidationRuntimeException(I18nProperties.getValidationError(Validations.validReportDateTime));
		}
		if (contact.getReportingUser() == null) {
			throw new ValidationRuntimeException(I18nProperties.getValidationError(Validations.validReportingUser));
		}
		if (contact.getCaze() == null) {
			throw new ValidationRuntimeException(I18nProperties.getValidationError(Validations.validCase));
		}
		if (contact.getPerson() == null) {
			throw new ValidationRuntimeException(I18nProperties.getValidationError(Validations.validPerson));
		}
	}

	@LocalBean
	@Stateless
	public static class ContactFacadeEjbLocal extends ContactFacadeEjb {

	}
	
}
