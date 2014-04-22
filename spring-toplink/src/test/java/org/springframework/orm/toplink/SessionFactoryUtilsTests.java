/*
 * Created on Mar 18, 2005
 *
 */

package org.springframework.orm.toplink;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import oracle.toplink.sessions.Session;

import org.easymock.EasyMock;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Juergen Hoeller
 * @author <a href="mailto:james.x.clark@oracle.com">James Clark</a>
 * @since 28.04.2005
 */
public class SessionFactoryUtilsTests {

	/**
	 * When no Session is bound and allowCreate is "false", we should throw an IllegalStateException.
	 * When no Session is bound, and allowCreate is "true", we should get a Session but it should not
	 * be bound to the Thread afterwards.
	 */
	@Test
	public void testNoSessionBound() {
		Session session = EasyMock.createMock(Session.class);
		SessionFactory factory = new SingleSessionFactory(session);
		EasyMock.expect(session.hasExternalTransactionController()).andReturn(false).times(1);
		EasyMock.replay(session);
		try {
			Session boundSession = SessionFactoryUtils.getSession(factory, false);
			fail();
		}
		catch (Throwable t) {
			assertTrue(t.getClass().equals(IllegalStateException.class));
		}

		Session boundSession = SessionFactoryUtils.getSession(factory, true);
		assertTrue(session == boundSession);
		assertFalse(TransactionSynchronizationManager.hasResource(factory));
		assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
	}

	/**
	 * When called with no previous Session bound, "allowCreate", and "allowSynchronization",
	 * Session should be returned, it should be bound to the Thread, and a synchronization listener
	 * should be in the list of thread synchronizations.
	 */
	public void testNoSessionBoundAllowAndInit() {
		Session session = EasyMock.createMock(Session.class);

		SessionFactory factory = new SingleSessionFactory(session);

		EasyMock.expect(session.hasExternalTransactionController()).andReturn(false).times(1);
		EasyMock.replay(session);

		Session boundSession = SessionFactoryUtils.getSession(factory, true);
		assertTrue(session == boundSession);

		SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(factory);
		assertTrue(holder == null);

		TransactionSynchronizationManager.initSynchronization();

		boundSession = SessionFactoryUtils.getSession(factory, true);
		assertTrue(session == boundSession);
		assertTrue(TransactionSynchronizationManager.getSynchronizations().size() == 1);
		assertTrue(TransactionSynchronizationManager.hasResource(factory));
		assertTrue(session == ((SessionHolder) TransactionSynchronizationManager.getResource(factory)).getSession());

		TransactionSynchronizationManager.clearSynchronization();
		TransactionSynchronizationManager.unbindResource(factory);
	}

	public void testNoSessionBoundAllowAndNoInit() {
		Session session = EasyMock.createMock(Session.class);

		SessionFactory factory = new SingleSessionFactory(session);

		session.hasExternalTransactionController();
		EasyMock.expect(session.hasExternalTransactionController()).andReturn(false).times(2);
		EasyMock.replay(session);

		Session boundSession = SessionFactoryUtils.getSession(factory, true);
		assertTrue(session == boundSession);

		SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(factory);
		assertTrue(holder == null);

		boundSession = SessionFactoryUtils.getSession(factory, true);
		assertTrue(session == boundSession);
		assertFalse(TransactionSynchronizationManager.hasResource(factory));
	}

}
