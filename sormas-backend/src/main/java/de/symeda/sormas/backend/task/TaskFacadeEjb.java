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
package de.symeda.sormas.backend.task;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.symeda.sormas.api.caze.CaseReferenceDto;
import de.symeda.sormas.api.contact.ContactReferenceDto;
import de.symeda.sormas.api.event.EventReferenceDto;
import de.symeda.sormas.api.i18n.I18nProperties;
import de.symeda.sormas.api.task.DashboardTaskDto;
import de.symeda.sormas.api.task.TaskContext;
import de.symeda.sormas.api.task.TaskCriteria;
import de.symeda.sormas.api.task.TaskDto;
import de.symeda.sormas.api.task.TaskFacade;
import de.symeda.sormas.api.task.TaskIndexDto;
import de.symeda.sormas.api.task.TaskStatus;
import de.symeda.sormas.api.task.TaskType;
import de.symeda.sormas.api.user.UserReferenceDto;
import de.symeda.sormas.api.user.UserRight;
import de.symeda.sormas.api.user.UserRole;
import de.symeda.sormas.api.utils.DataHelper;
import de.symeda.sormas.api.utils.SortProperty;
import de.symeda.sormas.backend.caze.Case;
import de.symeda.sormas.backend.caze.CaseFacadeEjb;
import de.symeda.sormas.backend.caze.CaseFacadeEjb.CaseFacadeEjbLocal;
import de.symeda.sormas.backend.caze.CaseService;
import de.symeda.sormas.backend.common.AbstractAdoService;
import de.symeda.sormas.backend.common.AbstractDomainObject;
import de.symeda.sormas.backend.common.CronService;
import de.symeda.sormas.backend.common.MessageType;
import de.symeda.sormas.backend.common.MessagingService;
import de.symeda.sormas.backend.common.NotificationDeliveryFailedException;
import de.symeda.sormas.backend.contact.Contact;
import de.symeda.sormas.backend.contact.ContactFacadeEjb;
import de.symeda.sormas.backend.contact.ContactService;
import de.symeda.sormas.backend.event.Event;
import de.symeda.sormas.backend.event.EventFacadeEjb;
import de.symeda.sormas.backend.event.EventService;
import de.symeda.sormas.backend.person.Person;
import de.symeda.sormas.backend.user.User;
import de.symeda.sormas.backend.user.UserFacadeEjb;
import de.symeda.sormas.backend.user.UserFacadeEjb.UserFacadeEjbLocal;
import de.symeda.sormas.backend.user.UserRoleConfigFacadeEjb.UserRoleConfigFacadeEjbLocal;
import de.symeda.sormas.backend.user.UserService;
import de.symeda.sormas.backend.util.DtoHelper;
import de.symeda.sormas.backend.util.ModelConstants;

@Stateless(name = "TaskFacade")
public class TaskFacadeEjb implements TaskFacade {

	@PersistenceContext(unitName = ModelConstants.PERSISTENCE_UNIT_NAME)
	protected EntityManager em;

	@EJB
	private TaskService taskService;
	@EJB
	private UserService userService;
	@EJB
	private CaseService caseService;
	@EJB
	private ContactService contactService;
	@EJB
	private EventService eventService;
	@EJB
	private UserFacadeEjbLocal userFacade;
	@EJB
	private CaseFacadeEjbLocal caseFacade;
	@EJB
	private MessagingService messagingService;
	@EJB
	private UserRoleConfigFacadeEjbLocal userRoleConfigFacade;

	private static final Logger logger = LoggerFactory.getLogger(TaskFacadeEjb.class);

	public Task fromDto(TaskDto source) {		
		if (source == null) {
			return null;
		}

		Task target = taskService.getByUuid(source.getUuid());
		if (target == null) {
			target = new Task();
			target.setUuid(source.getUuid());
			if (source.getCreationDate() != null) {
				target.setCreationDate(new Timestamp(source.getCreationDate().getTime()));
			}
		} 
		DtoHelper.validateDto(source, target);

		target.setAssigneeUser(userService.getByReferenceDto(source.getAssigneeUser()));
		target.setAssigneeReply(source.getAssigneeReply());
		target.setCreatorUser(userService.getByReferenceDto(source.getCreatorUser()));
		target.setCreatorComment(source.getCreatorComment());
		target.setPriority(source.getPriority());
		target.setDueDate(source.getDueDate());
		target.setSuggestedStart(source.getSuggestedStart());
		target.setPerceivedStart(source.getPerceivedStart());
		// TODO is this a good place to do this?
		if (target.getTaskStatus() != source.getTaskStatus()) {
			target.setStatusChangeDate(new Date());
		} else {
			target.setStatusChangeDate(source.getStatusChangeDate());
		}
		target.setTaskStatus(source.getTaskStatus());
		target.setTaskType(source.getTaskType());

		target.setClosedLat(source.getClosedLat());
		target.setClosedLon(source.getClosedLon());
		target.setClosedLatLonAccuracy(source.getClosedLatLonAccuracy());

		target.setTaskContext(source.getTaskContext());
		if (source.getTaskContext() != null) {
			switch (source.getTaskContext()) {
			case CASE:
				target.setCaze(caseService.getByReferenceDto(source.getCaze()));
				target.setContact(null);
				target.setEvent(null);
				break;
			case CONTACT:
				target.setCaze(null);
				target.setContact(contactService.getByReferenceDto(source.getContact()));
				target.setEvent(null);
				break;
			case EVENT:
				target.setCaze(null);
				target.setContact(null);
				target.setEvent(eventService.getByReferenceDto(source.getEvent()));
				break;
			case GENERAL:
				target.setCaze(null);
				target.setContact(null);
				target.setEvent(null);
				break;
			default:
				throw new UnsupportedOperationException(source.getTaskContext() + " is not implemented");
			}
		} else {
			target.setCaze(null);
			target.setContact(null);
			target.setEvent(null);
		}

		return target;
	}

	public TaskDto toDto(Task task) {

		if (task == null) {
			return null;
		}

		TaskDto target = new TaskDto();
		Task source = task;

		target.setCreationDate(source.getCreationDate());
		target.setChangeDate(source.getChangeDate());
		target.setUuid(source.getUuid());

		target.setAssigneeUser(UserFacadeEjb.toReferenceDto(source.getAssigneeUser()));
		target.setAssigneeReply(source.getAssigneeReply());
		target.setCreatorUser(UserFacadeEjb.toReferenceDto(source.getCreatorUser()));
		target.setCreatorComment(source.getCreatorComment());
		target.setPriority(source.getPriority());
		target.setDueDate(source.getDueDate());
		target.setSuggestedStart(source.getSuggestedStart());
		target.setPerceivedStart(source.getPerceivedStart());
		target.setStatusChangeDate(source.getStatusChangeDate());
		target.setTaskContext(source.getTaskContext());
		target.setTaskStatus(source.getTaskStatus());
		target.setTaskType(source.getTaskType());	
		target.setCaze(CaseFacadeEjb.toReferenceDto(source.getCaze()));
		target.setContact(ContactFacadeEjb.toReferenceDto(source.getContact()));
		target.setEvent(EventFacadeEjb.toReferenceDto(source.getEvent()));

		target.setClosedLat(source.getClosedLat());
		target.setClosedLon(source.getClosedLon());
		target.setClosedLatLonAccuracy(source.getClosedLatLonAccuracy());

		return target;
	}

	@Override
	public TaskDto saveTask(TaskDto dto) {
		Task ado = fromDto(dto);
		taskService.ensurePersisted(ado);

		// once we have to handle additional logic this should be moved to it's own function or even class 
		if (ado.getTaskType() == TaskType.CASE_INVESTIGATION && ado.getCaze() != null) {
			caseFacade.updateInvestigationByTask(ado.getCaze());
		}
		
		if (ado.getTaskType() == TaskType.CONTACT_FOLLOW_UP && ado.getTaskStatus() == TaskStatus.DONE && ado.getContact() != null) {
			List<User> messageRecipients = userService.getAllByRegionAndUserRoles(ado.getContact().getCaze().getRegion(), 
					UserRole.SURVEILLANCE_SUPERVISOR, UserRole.CASE_SUPERVISOR, UserRole.CONTACT_SUPERVISOR);
			for (User recipient : messageRecipients) {
				try {
					messagingService.sendMessage(recipient, I18nProperties.getString(MessagingService.SUBJECT_VISIT_COMPLETED), 
							String.format(I18nProperties.getString(MessagingService.CONTENT_VISIT_COMPLETED), DataHelper.getShortUuid(ado.getContact().getUuid()), DataHelper.getShortUuid(ado.getAssigneeUser().getUuid())), 
							MessageType.EMAIL, MessageType.SMS);
				} catch (NotificationDeliveryFailedException e) {
					logger.error(String.format("NotificationDeliveryFailedException when trying to notify supervisors about the completion of a follow-up visit. "
							+ "Failed to send " + e.getMessageType() + " to user with UUID %s.", recipient.getUuid()));
				}
			}
		}

		return toDto(ado);	
	}

	@Override
	public List<String> getAllActiveUuids(String userUuid) {
		User user = userService.getByUuid(userUuid);

		if (user == null) {
			return Collections.emptyList();
		}

		return taskService.getAllActiveUuids(user);
	}

	@Override
	public List<TaskDto> getAllActiveTasksAfter(Date date, String userUuid) {
		User user = userService.getByUuid(userUuid);

		if (user == null) {
			return Collections.emptyList();
		}

		return taskService.getAllActiveTasksAfter(date, user).stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public long count(String userUuid, TaskCriteria taskCriteria) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Task> task = cq.from(Task.class);

		Predicate filter = null;
		if (userUuid != null 
				&& (taskCriteria == null || !taskCriteria.hasContextCriteria())) {
			User user = userService.getByUuid(userUuid);		
			filter = taskService.createUserFilter(cb, cq, task, user);
		}

		if (taskCriteria != null) {
			Predicate criteriaFilter = taskService.buildCriteriaFilter(taskCriteria, cb, task);
			filter = AbstractAdoService.and(cb, filter, criteriaFilter);
		}

		if (filter != null) {
			cq.where(filter);
		}
		
		cq.select(cb.count(task));
		return em.createQuery(cq).getSingleResult();
	}
	
	@Override
	public List<TaskIndexDto> getIndexList(String userUuid, TaskCriteria taskCriteria, Integer first, Integer max, List<SortProperty> sortProperties) {

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<TaskIndexDto> cq = cb.createQuery(TaskIndexDto.class);
		Root<Task> task = cq.from(Task.class);
		Join<Task, Case> caze = task.join(Task.CAZE, JoinType.LEFT);
		Join<Case, Person> cazePerson = caze.join(Case.PERSON, JoinType.LEFT);
		Join<Task, Event> event = task.join(Task.EVENT, JoinType.LEFT);
		Join<Task, Contact> contact = task.join(Task.CONTACT, JoinType.LEFT);
		Join<Contact, Person> contactPerson = contact.join(Contact.PERSON, JoinType.LEFT);
		Join<Contact, Person> contactCasePerson = contact.join(Contact.CAZE, JoinType.LEFT).join(Case.PERSON, JoinType.LEFT);
		Join<Task, User> creator = task.join(Task.CREATOR_USER, JoinType.LEFT);
		Join<Task, User> assignee = task.join(Task.ASSIGNEE_USER, JoinType.LEFT);

		cq.multiselect(task.get(Task.UUID), task.get(Task.TASK_CONTEXT), 
				caze.get(Case.UUID), cazePerson.get(Person.FIRST_NAME), cazePerson.get(Person.LAST_NAME),
				event.get(Event.UUID), event.get(Event.DISEASE), event.get(Event.DISEASE_DETAILS), event.get(Event.EVENT_STATUS), event.get(Event.EVENT_DATE),
				contact.get(Contact.UUID), contactPerson.get(Person.FIRST_NAME), contactPerson.get(Person.LAST_NAME),
				contactCasePerson.get(Person.FIRST_NAME), contactCasePerson.get(Person.LAST_NAME),
				task.get(Task.TASK_TYPE), task.get(Task.PRIORITY), 
				task.get(Task.DUE_DATE), task.get(Task.SUGGESTED_START), task.get(Task.TASK_STATUS),
				creator.get(User.UUID), creator.get(User.FIRST_NAME), creator.get(User.LAST_NAME), task.get(Task.CREATOR_COMMENT),
				assignee.get(User.UUID), assignee.get(User.FIRST_NAME), assignee.get(User.LAST_NAME), task.get(Task.ASSIGNEE_REPLY)
				);

		Predicate filter = null;
		if (userUuid != null 
				&& (taskCriteria == null || !taskCriteria.hasContextCriteria())) {
			User user = userService.getByUuid(userUuid);		
			filter = taskService.createUserFilter(cb, cq, task, user);
		}

		if (taskCriteria != null) {
			Predicate criteriaFilter = taskService.buildCriteriaFilter(taskCriteria, cb, task);
			filter = AbstractAdoService.and(cb, filter, criteriaFilter);
		}

		if (filter != null) {
			cq.where(filter);
		}

		List<Order> order = new ArrayList<Order>();
		if (sortProperties != null && sortProperties.size() > 0) {
			for (SortProperty sortProperty : sortProperties) {
				Expression<?> expression;
				switch (sortProperty.propertyName) {
				case TaskIndexDto.UUID:
				case TaskIndexDto.ASSIGNEE_REPLY:
				case TaskIndexDto.CREATOR_COMMENT:
				case TaskIndexDto.PRIORITY:
				case TaskIndexDto.DUE_DATE:
				case TaskIndexDto.SUGGESTED_START:
				case TaskIndexDto.TASK_CONTEXT:
				case TaskIndexDto.TASK_STATUS:
				case TaskIndexDto.TASK_TYPE:
					expression = task.get(sortProperty.propertyName);
					break;
				case TaskIndexDto.ASSIGNEE_USER:
					expression = assignee.get(User.USER_NAME);
					break;
				case TaskIndexDto.CREATOR_USER:
					expression = creator.get(User.USER_NAME);
					break;
				case TaskIndexDto.CAZE:
					expression = cazePerson.get(Person.LAST_NAME);
					order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
					expression = cazePerson.get(Person.FIRST_NAME);
					break;
				case TaskIndexDto.CONTACT:
					expression = contactPerson.get(Person.LAST_NAME);
					order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
					expression = contactPerson.get(Person.FIRST_NAME);
					break;
				case TaskIndexDto.EVENT:
					expression = event.get(Event.EVENT_DATE);
					break;
				default:
					throw new IllegalArgumentException(sortProperty.propertyName);
				}
				order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
			}
		}
		order.add(cb.desc(task.get(Task.DUE_DATE)));
		cq.orderBy(order);

		if (first != null && max != null) {
			return em.createQuery(cq).setFirstResult(first).setMaxResults(max).getResultList();
		} else {
			return em.createQuery(cq).getResultList();
		}
	}

	@Override
	public List<TaskDto> getAllByCase(CaseReferenceDto caseRef) {
		if(caseRef == null) {
			return Collections.emptyList();
		}

		return taskService.findBy(new TaskCriteria().caze(caseRef))
				.stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public List<TaskDto> getAllByContact(ContactReferenceDto contactRef) {
		if(contactRef == null) {
			return Collections.emptyList();
		}

		return taskService.findBy(new TaskCriteria().contact(contactRef))
				.stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public List<TaskDto> getAllByEvent(EventReferenceDto eventRef) {
		if(eventRef == null) {
			return Collections.emptyList();
		}

		return taskService.findBy(new TaskCriteria().event(eventRef))
				.stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public List<TaskDto> getByUuids(List<String> uuids) {
		return taskService.getByUuids(uuids)
				.stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public List<TaskDto> getAllPendingByCase(CaseReferenceDto caseRef) {
		if (caseRef == null) {
			return Collections.emptyList();
		}

		return taskService.findBy(new TaskCriteria().caze(caseRef).taskStatus(TaskStatus.PENDING))
				.stream()
				.map(c -> toDto(c))
				.collect(Collectors.toList());
	}

	@Override
	public List<DashboardTaskDto> getAllByUserForDashboard(TaskStatus taskStatus, Date from, Date to, String userUuid) {

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<DashboardTaskDto> cq = cb.createQuery(DashboardTaskDto.class);
		Root<Task> task = cq.from(Task.class);

		TaskCriteria taskCriteria = new TaskCriteria().assigneeUser(new UserReferenceDto(userUuid));
		if (taskStatus != null) {
			taskCriteria.taskStatus(taskStatus);
		}
		if (from != null || to != null) {
			taskCriteria.statusChangeDateBetween(from, to);
		}

		Predicate filter = taskService.buildCriteriaFilter(taskCriteria, cb, task);

		List<DashboardTaskDto> result;
		if (filter != null) {
			cq.where(filter);
			cq.multiselect(
					task.get(Task.PRIORITY),
					task.get(Task.TASK_STATUS)
					);

			result = em.createQuery(cq).getResultList();
		} else {
			result = Collections.emptyList();
		}

		return result;
	}

	@Override
	public long getPendingTaskCountByCase(CaseReferenceDto caseRef) {
		if(caseRef == null) {
			return 0;
		}

		return taskService.getCount(new TaskCriteria().caze(caseRef).taskStatus(TaskStatus.PENDING));
	}

	@Override
	public long getPendingTaskCountByContact(ContactReferenceDto contactRef) {
		if(contactRef == null) {
			return 0;
		}

		return taskService.getCount(new TaskCriteria().contact(contactRef).taskStatus(TaskStatus.PENDING));
	}

	@Override
	public long getPendingTaskCountByEvent(EventReferenceDto eventRef) {
		if(eventRef == null) {
			return 0;
		}

		return taskService.getCount(new TaskCriteria().event(eventRef).taskStatus(TaskStatus.PENDING));
	}

	@Override
	public long getPendingTaskCount(String userUuid) {
		return taskService.getCount(new TaskCriteria().taskStatus(TaskStatus.PENDING).assigneeUser(new UserReferenceDto(userUuid)));
	}

	@Override
	public TaskDto getByUuid(String uuid) {
		return toDto(taskService.getByUuid(uuid));
	}

	@Override
	public void deleteTask(TaskDto taskDto, String userUuid) {
		User user = userService.getByUuid(userUuid);
		if (!userRoleConfigFacade.getEffectiveUserRights(user.getUserRoles().toArray(new UserRole[user.getUserRoles().size()])).contains(UserRight.TASK_DELETE)) {
			throw new UnsupportedOperationException("User " + userUuid + " is not allowed to delete tasks.");
		}

		Task task = taskService.getByUuid(taskDto.getUuid());
		taskService.delete(task);
	}

	@Override
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public void sendNewAndDueTaskMessages() {
		Calendar calendar = Calendar.getInstance();
		Date now = new Date();
		calendar.setTime(now);
		calendar.add(Calendar.MINUTE, CronService.TASK_UPDATE_INTERVAL * -1);
		Date before = calendar.getTime();

		List<Task> startingTasks = taskService.findBy(new TaskCriteria().taskStatus(TaskStatus.PENDING).startDateBetween(before, now));
		for (Task task : startingTasks) {
			TaskContext context = task.getTaskContext();
			AbstractDomainObject associatedEntity = context == TaskContext.CASE ? task.getCaze() : 
					context == TaskContext.CONTACT ? task.getContact() : 
					context == TaskContext.EVENT ? task.getEvent() : null;
			if (task.getAssigneeUser() != null && task.getAssigneeUser().isSupervisor() || task.getAssigneeUser().getUserRoles().contains(UserRole.NATIONAL_USER)) {
				try {
					String subject = I18nProperties.getString(MessagingService.SUBJECT_TASK_START);
					String content = context == TaskContext.GENERAL ? 
								String.format(I18nProperties.getString(MessagingService.CONTENT_TASK_START_GENERAL), task.getTaskType().toString()) :
								String.format(I18nProperties.getString(MessagingService.CONTENT_TASK_START_SPECIFIC), task.getTaskType().toString(), 
								context.toString() + " " + DataHelper.getShortUuid(associatedEntity.getUuid()));

					messagingService.sendMessage(userService.getByUuid(task.getAssigneeUser().getUuid()), subject, content, MessageType.EMAIL, MessageType.SMS);
				} catch (NotificationDeliveryFailedException e) {
					logger.error(String.format("EmailDeliveryFailedException when trying to notify a user about a starting task. "
							+ "Failed to send " + e.getMessageType() + " to user with UUID %s.", task.getAssigneeUser().getUuid()));
				}
			}
		}

		List<Task> dueTasks = taskService.findBy(new TaskCriteria().taskStatus(TaskStatus.PENDING).dueDateBetween(before, now));
		for (Task task : dueTasks) {
			TaskContext context = task.getTaskContext();
			AbstractDomainObject associatedEntity = context == TaskContext.CASE ? task.getCaze() : 
					context == TaskContext.CONTACT ? task.getContact() : 
					context == TaskContext.EVENT ? task.getEvent() : null;
			if (task.getAssigneeUser() != null && (task.getAssigneeUser().isSupervisor() 
					|| task.getAssigneeUser().getUserRoles().contains(UserRole.NATIONAL_USER))) {
				try {
					String subject = I18nProperties.getString(MessagingService.SUBJECT_TASK_DUE);
					String content = context == TaskContext.GENERAL ? 
								String.format(I18nProperties.getString(MessagingService.CONTENT_TASK_DUE_GENERAL), task.getTaskType().toString()) :
								String.format(I18nProperties.getString(MessagingService.CONTENT_TASK_DUE_SPECIFIC), task.getTaskType().toString(), 
								context.toString() + (associatedEntity != null ? (" " + DataHelper.getShortUuid(associatedEntity.getUuid())) : ""));

					messagingService.sendMessage(userService.getByUuid(task.getAssigneeUser().getUuid()), subject, content, MessageType.EMAIL, MessageType.SMS);
				} catch (NotificationDeliveryFailedException e) {
						logger.error(String.format("EmailDeliveryFailedException when trying to notify a user about a due task. "
							+ "Failed to send " + e.getMessageType() + " to user with UUID %s.", task.getAssigneeUser().getUuid()));
				}
			}
		}
	}
	
	@LocalBean
	@Stateless
	public static class TaskFacadeEjbLocal extends TaskFacadeEjb {
	}	
}

