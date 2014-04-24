/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.orm.toplink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import oracle.toplink.exceptions.TopLinkException;
import oracle.toplink.expressions.Expression;
import oracle.toplink.queryframework.Call;
import oracle.toplink.queryframework.DatabaseQuery;
import oracle.toplink.queryframework.ReadObjectQuery;
import oracle.toplink.sessions.ObjectCopyingPolicy;
import oracle.toplink.sessions.Session;
import oracle.toplink.sessions.UnitOfWork;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Helper class that simplifies TopLink data access code, and converts
 * TopLinkExceptions into unchecked DataAccessExceptions, following the
 * <code>org.springframework.dao</code> exception hierarchy.
 *
 * <p>The central method is <code>execute</code>, supporting TopLink access code
 * implementing the {@link TopLinkCallback} interface. It provides TopLink Session
 * handling such that neither the TopLinkCallback implementation nor the calling
 * code needs to explicitly care about retrieving/closing TopLink Sessions,
 * or handling Session lifecycle exceptions. For typical single step actions,
 * there are various convenience methods (read, readAll, merge, delete, etc).
 *
 * <p>Can be used within a service implementation via direct instantiation
 * with a SessionFactory reference, or get prepared in an application context
 * and given to services as bean reference. Note: The SessionFactory should
 * always be configured as bean in the application context, in the first case
 * given to the service directly, in the second case to the prepared template.
 *
 * <p>This class can be considered as direct alternative to working with the raw
 * TopLink Session API (through <code>SessionFactoryUtils.getSession()</code>).
 * The major advantage is its automatic conversion to DataAccessExceptions, the
 * major disadvantage that no checked application exceptions can get thrown from
 * within data access code. Corresponding checks and the actual throwing of such
 * exceptions can often be deferred to after callback execution, though.
 *
 * <p>{@link LocalSessionFactoryBean} is the preferred way of obtaining a reference
 * to a specific TopLink SessionFactory. It will usually be configured to
 * create ClientSessions for a ServerSession held by it, allowing for seamless
 * multi-threaded execution. The Spring application context will manage its lifecycle,
 * initializing and shutting down the factory as part of the application.
 *
 * <p>Thanks to Slavik Markovich for implementing the initial TopLink support prototype!
 *
 * @author Juergen Hoeller
 * @author <a href="mailto:james.x.clark@oracle.com">James Clark</a>
 * @since 1.2
 * @see #setSessionFactory
 * @see TopLinkCallback
 * @see oracle.toplink.sessions.Session
 * @see TopLinkInterceptor
 * @see LocalSessionFactoryBean
 * @see TopLinkTransactionManager
 * @see org.springframework.transaction.jta.JtaTransactionManager
 */
public class TopLinkTemplate extends TopLinkAccessor implements TopLinkOperations {

	private boolean allowCreate = true;


	/**
	 * Create a new TopLinkTemplate instance.
	 */
	public TopLinkTemplate() {
	}

	/**
	 * Create a new TopLinkTemplate instance.
	 */
	public TopLinkTemplate(SessionFactory sessionFactory) {
		setSessionFactory(sessionFactory);
		afterPropertiesSet();
	}

	/**
	 * Create a new TopLinkTemplate instance.
	 * @param allowCreate if a new Session should be created if no thread-bound found
	 */
	public TopLinkTemplate(SessionFactory sessionFactory, boolean allowCreate) {
		setSessionFactory(sessionFactory);
		setAllowCreate(allowCreate);
		afterPropertiesSet();
	}

	/**
	 * Set if a new Session should be created when no transactional Session
	 * can be found for the current thread.
	 * <p>TopLinkTemplate is aware of a corresponding Session bound to the
	 * current thread, for example when using TopLinkTransactionManager.
	 * If allowCreate is true, a new non-transactional Session will be created
	 * if none found, which needs to be closed at the end of the operation.
	 * If false, an IllegalStateException will get thrown in this case.
	 * @see SessionFactoryUtils#getSession(SessionFactory, boolean)
	 */
	public void setAllowCreate(boolean allowCreate) {
		this.allowCreate = allowCreate;
	}

	/**
	 * Return if a new Session should be created if no thread-bound found.
	 */
	public boolean isAllowCreate() {
		return this.allowCreate;
	}


	public <T> T execute(TopLinkCallback<T> action) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");

		Session session = SessionFactoryUtils.getSession(getSessionFactory(), this.allowCreate);
		try {
			return action.doInTopLink(session);
		}
		catch (TopLinkException ex) {
			throw convertTopLinkAccessException(ex);
		}
		catch (RuntimeException ex) {
			// callback code threw application exception
			throw ex;
		}
		finally {
			SessionFactoryUtils.releaseSession(session, getSessionFactory());
		}
	}

	public List executeFind(TopLinkCallback<?> action) throws DataAccessException {
		Object result = execute(action);
		if (result != null && !(result instanceof List)) {
			throw new InvalidDataAccessApiUsageException(
					"Result object returned from TopLinkCallback isn't a List: [" + result + "]");
		}
		return (List) result;
	}


	//-------------------------------------------------------------------------
	// Convenience methods for executing generic queries
	//-------------------------------------------------------------------------

	public Object executeNamedQuery(Class<?> entityClass, String queryName) throws DataAccessException {
		return executeNamedQuery(entityClass, queryName, null, false);
	}

	public Object executeNamedQuery(Class<?> entityClass, String queryName, boolean enforceReadOnly)
			throws DataAccessException {

		return executeNamedQuery(entityClass, queryName, null, enforceReadOnly);
	}

	public Object executeNamedQuery(Class<?> entityClass, String queryName, Object[] args)
			throws DataAccessException {

		return executeNamedQuery(entityClass, queryName, args, false);
	}

	public Object executeNamedQuery(
			final Class<?> entityClass, final String queryName, final Object[] args, final boolean enforceReadOnly)
			throws DataAccessException {

		return execute(new SessionReadCallback<Object>(enforceReadOnly) {
			protected Object readFromSession(Session session) throws TopLinkException {
				if (args != null) {
					return session.executeQuery(queryName, entityClass, new Vector(Arrays.asList(args)));
				}
				else {
					return session.executeQuery(queryName, entityClass, new Vector());
				}
			}
		});
	}

	public Object executeQuery(DatabaseQuery query) throws DataAccessException {
		return executeQuery(query, null, false);
	}

	public Object executeQuery(DatabaseQuery query, boolean enforceReadOnly) throws DataAccessException {
		return executeQuery(query, null, enforceReadOnly);
	}

	public Object executeQuery(DatabaseQuery query, Object[] args) throws DataAccessException {
		return executeQuery(query, args, false);
	}

	public Object executeQuery(final DatabaseQuery query, final Object[] args, final boolean enforceReadOnly)
			throws DataAccessException {

		return execute(new SessionReadCallback<Object>(enforceReadOnly) {
			protected Object readFromSession(Session session) throws TopLinkException {
				if (args != null) {
					return session.executeQuery(query, new Vector(Arrays.asList(args)));
				}
				else {
					return session.executeQuery(query);
				}
			}
		});
	}


	//-------------------------------------------------------------------------
	// Convenience methods for reading a specific set of objects
	//-------------------------------------------------------------------------

	public <T> List<T> readAll(Class<T> entityClass) throws DataAccessException {
		return readAll(entityClass, false);
	}

	
	@SuppressWarnings("unchecked")
	public <T> List<T> readAll(final Class<T> entityClass, final boolean enforceReadOnly) throws DataAccessException {
		return executeFind(new SessionReadCallback<List<T>>(enforceReadOnly) {
			protected List<T> readFromSession(Session session) throws TopLinkException {
				return session.readAllObjects(entityClass);
			}
		});
	}

	public <T> List<T> readAll(Class<T> entityClass, Expression expression) throws DataAccessException {
		return readAll(entityClass, expression, false);
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> readAll(final Class<T> entityClass, final Expression expression, final boolean enforceReadOnly)
			throws DataAccessException {
		return executeFind(new SessionReadCallback<List<T>>(enforceReadOnly) {
			protected List<T> readFromSession(Session session) throws TopLinkException {
				return session.readAllObjects(entityClass, expression);
			}
		});
	}

	public <T> List<T> readAll(Class<T> entityClass, Call call) throws DataAccessException {
		return readAll(entityClass, call, false);
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> readAll(final Class<T> entityClass, final Call call, final boolean enforceReadOnly)
			throws DataAccessException {
		return executeFind(new SessionReadCallback<List<T>>(enforceReadOnly) {
			protected List<T> readFromSession(Session session) throws TopLinkException {
				return session.readAllObjects(entityClass, call);
			}
		});
	}

	public <T> T read(Class<T> entityClass, Expression expression) throws DataAccessException {
		return read(entityClass, expression, false);
	}

	public <T> T read(final Class<T> entityClass, final Expression expression, final boolean enforceReadOnly)
			throws DataAccessException {
		return execute(new SessionReadCallback<T>(enforceReadOnly) {
			@SuppressWarnings("unchecked")
			protected T readFromSession(Session session) throws TopLinkException {
				return (T)session.readObject(entityClass, expression);
			}
		});
	}

	public <T> T read(Class<T> entityClass, Call call) throws DataAccessException {
		return read(entityClass, call, false);
	}

	public <T> T read(final Class<T> entityClass, final Call call, final boolean enforceReadOnly)
			throws DataAccessException {
		return execute(new SessionReadCallback<T>(enforceReadOnly) {
			@SuppressWarnings("unchecked")
			protected T readFromSession(Session session) throws TopLinkException {
				return (T)session.readObject(entityClass, call);
			}
		});
	}


	//-------------------------------------------------------------------------
	// Convenience methods for reading an individual object by id
	//-------------------------------------------------------------------------

	public <T> T readById(Class<T> entityClass, Object id) throws DataAccessException {
		return readById(entityClass, id, false);
	}

	public <T> T readById(Class<T> entityClass, Object id, boolean enforceReadOnly) throws DataAccessException {
		return readById(entityClass, new Object[] {id}, enforceReadOnly);
	}

	public <T> T readById(Class<T> entityClass, Object[] keys) throws DataAccessException {
		return readById(entityClass, keys, false);
	}

	@SuppressWarnings("unchecked")
	public <T> T readById(final Class<T> entityClass, final Object[] keys, final boolean enforceReadOnly)
			throws DataAccessException {

		Assert.isTrue(keys != null && keys.length > 0, "Non-empty keys or id is required");

		ReadObjectQuery query = new ReadObjectQuery(entityClass);
		query.setSelectionKey(new Vector(Arrays.asList(keys)));
		Object result = executeQuery(query, enforceReadOnly);

		if (result == null) {
			Object identifier = (keys.length == 1 ? keys[0] : StringUtils.arrayToCommaDelimitedString(keys));
			throw new ObjectRetrievalFailureException(entityClass, identifier);
		}
		return (T)result;
	}

	public <T> T readAndCopy(Class<T> entityClass, Object id) throws DataAccessException {
		return readAndCopy(entityClass, id, false);
	}

	public <T> T readAndCopy(Class<T> entityClass, Object id, boolean enforceReadOnly)
			throws DataAccessException {

		T entity = readById(entityClass, id, enforceReadOnly);
		return copy(entity);
	}

	public <T> T readAndCopy(Class<T> entityClass, Object[] keys) throws DataAccessException {
		return readAndCopy(entityClass, keys, false);
	}

	public <T> T readAndCopy(Class<T> entityClass, Object[] keys, boolean enforceReadOnly)
			throws DataAccessException {

		T entity = readById(entityClass, keys, enforceReadOnly);
		return (T)copy(entity);
	}


	//-------------------------------------------------------------------------
	// Convenience methods for copying and refreshing objects
	//-------------------------------------------------------------------------

	public <T> T copy(T entity) throws DataAccessException {
		ObjectCopyingPolicy copyingPolicy = new ObjectCopyingPolicy();
		copyingPolicy.cascadeAllParts();
		copyingPolicy.setShouldResetPrimaryKey(false);
		return copy(entity, copyingPolicy);
	}

	public <T> T copy(final T entity, final ObjectCopyingPolicy copyingPolicy)
			throws DataAccessException {

		return execute(new TopLinkCallback<T>() {
			@SuppressWarnings("unchecked")
			public T doInTopLink(Session session) throws TopLinkException {
				return (T)session.copyObject(entity, copyingPolicy);
			}
		});
	}

	@SuppressWarnings("rawtypes")
	public List copyAll(Collection<?> entities) throws DataAccessException {
		ObjectCopyingPolicy copyingPolicy = new ObjectCopyingPolicy();
		copyingPolicy.cascadeAllParts();
		copyingPolicy.setShouldResetPrimaryKey(false);
		return copyAll(entities, copyingPolicy);
	}

	@SuppressWarnings("rawtypes")
	public List copyAll(final Collection<?> entities, final ObjectCopyingPolicy copyingPolicy)
			throws DataAccessException {
		return execute(new TopLinkCallback<List>() {
			@SuppressWarnings("unchecked")
			public List doInTopLink(Session session) throws TopLinkException {
				List result = new ArrayList(entities.size());
				for (Iterator<?> it = entities.iterator(); it.hasNext();) {
					Object entity = it.next();
					result.add(session.copyObject(entity, copyingPolicy));
				}
				return result;
			}
		});
	}

	public <T> T refresh(T entity) throws DataAccessException {
		return refresh(entity, false);
	}

	public <T> T refresh(final T entity, final boolean enforceReadOnly) throws DataAccessException {
		return execute(new SessionReadCallback<T>(enforceReadOnly) {
			@SuppressWarnings("unchecked")
			protected T readFromSession(Session session) throws TopLinkException {
				return (T)session.refreshObject(entity);
			}
		});
	}

	@SuppressWarnings("rawtypes")
	public List refreshAll(Collection<?> entities) throws DataAccessException {
		return refreshAll(entities, false);
	}

	@SuppressWarnings("rawtypes")
	public List refreshAll(final Collection<?> entities, final boolean enforceReadOnly) throws DataAccessException {
		return execute(new SessionReadCallback<List>(enforceReadOnly) {
			@SuppressWarnings("unchecked")
			protected List readFromSession(Session session) throws TopLinkException {
				List result = new ArrayList(entities.size());
				for (Iterator<?> it = entities.iterator(); it.hasNext();) {
					Object entity = it.next();
					result.add(session.refreshObject(entity));
				}
				return result;
			}
		});
	}


	//-------------------------------------------------------------------------
	// Convenience methods for persisting and deleting objects
	//-------------------------------------------------------------------------

	public <T> T register(final T entity) {
		return execute(new UnitOfWorkCallback<T>() {
			@SuppressWarnings("unchecked")
			protected T doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return (T)unitOfWork.registerObject(entity);
			}
		});
	}

	@SuppressWarnings("rawtypes")
	public List registerAll(final Collection<?> entities) {
		return execute(new UnitOfWorkCallback<List>() {
			protected List doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return unitOfWork.registerAllObjects(entities);
			}
		});
	}

	public void registerNew(final Object entity) {
		execute(new UnitOfWorkCallback<Object>() {
			protected Object doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return unitOfWork.registerNewObject(entity);
			}
		});
	}

	public <T> T registerExisting(final T entity) {
		return execute(new UnitOfWorkCallback<T>() {
			@SuppressWarnings("unchecked")
			protected T doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return (T)unitOfWork.registerExistingObject(entity);
			}
		});
	}

	public <T> T merge(final T entity) throws DataAccessException {
		return execute(new UnitOfWorkCallback<T>() {
			@SuppressWarnings("unchecked")
			protected T doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return (T)unitOfWork.mergeClone(entity);
			}
		});
	}

	public <T> T deepMerge(final T entity) throws DataAccessException {
		return execute(new UnitOfWorkCallback<T>() {
			@SuppressWarnings("unchecked")
			protected T doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return (T)unitOfWork.deepMergeClone(entity);
			}
		});
	}

	public <T> T shallowMerge(final T entity) throws DataAccessException {
		return execute(new UnitOfWorkCallback<T>() {
			@SuppressWarnings("unchecked")
			protected T doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return (T)unitOfWork.shallowMergeClone(entity);
			}
		});
	}

	public <T> T mergeWithReferences(final T entity) throws DataAccessException {
		return execute(new UnitOfWorkCallback<T>() {
			@SuppressWarnings("unchecked")
			protected T doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return (T)unitOfWork.mergeCloneWithReferences(entity);
			}
		});
	}

	public void delete(final Object entity) throws DataAccessException {
		execute(new UnitOfWorkCallback<Object>() {
			protected Object doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				return unitOfWork.deleteObject(entity);
			}
		});
	}

	public void deleteAll(final Collection<?> entities) throws DataAccessException {
		execute(new UnitOfWorkCallback<Object>() {
			protected Object doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				unitOfWork.deleteAllObjects(entities);
				return null;
			}
		});
	}

	public void assignSequenceNumber(final Object entity) {
		execute(new UnitOfWorkCallback<Object>() {
			protected Object doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				unitOfWork.assignSequenceNumber(entity);
				return null;
			}
		});
	}

	public void assignSequenceNumbers() {
		execute(new UnitOfWorkCallback<Object>() {
			protected Object doInUnitOfWork(UnitOfWork unitOfWork) throws TopLinkException {
				unitOfWork.assignSequenceNumbers();
				return null;
			}
		});
	}
}
