/*
 * Created on Mar 20, 2005
 *
 */

package org.springframework.orm.toplink;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import oracle.toplink.sessions.Session;

import org.aopalliance.intercept.MethodInvocation;
import org.easymock.EasyMock;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Juergen Hoeller
 * @author <a href="mailto:james.x.clark@oracle.com">James Clark</a>
 * @since 28.04.2005
 */
public class TopLinkInterceptorTests {

	@Test
	public void testInterceptorWithNoSessionBoundAndNoSynchronizations() throws Throwable {
		Session session = EasyMock.createNiceMock(Session.class);
		MethodInvocation methodInvocation = EasyMock.createNiceMock(MethodInvocation.class);

		SessionFactory factory = new SingleSessionFactory(session);

		TopLinkInterceptor interceptor = new TopLinkInterceptor();
		interceptor.setSessionFactory(factory);

		EasyMock.expect(methodInvocation.proceed()).andReturn(null).times(1);
		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(methodInvocation);
		EasyMock.replay(session);

		try {
			interceptor.invoke(methodInvocation);
		}
		catch (Throwable t) {
			System.out.println(t);
			t.printStackTrace();
			fail();
		}

		assertFalse(TransactionSynchronizationManager.hasResource(factory));

		EasyMock.verify(session);
		EasyMock.verify(methodInvocation);
	}

	@Test
	public void testInterceptorWithNoSessionBoundAndSynchronizationsActive() {
		Session session = EasyMock.createNiceMock(Session.class);
		MethodInvocation methodInvocation = EasyMock.createNiceMock(MethodInvocation.class);

		SessionFactory factory = new SingleSessionFactory(session);

		TopLinkInterceptor interceptor = new TopLinkInterceptor();
		interceptor.setSessionFactory(factory);

		try {
			EasyMock.expect(methodInvocation.proceed()).andReturn(null).times(1);
		}
		catch (Throwable e) {
			fail();
		}

		EasyMock.replay(session);
		EasyMock.replay(methodInvocation);

		TransactionSynchronizationManager.initSynchronization();
		try {
			interceptor.invoke(methodInvocation);
		}
		catch (Throwable t) {
			fail();
		}

		assertTrue(TransactionSynchronizationManager.hasResource(factory));
		assertTrue(TransactionSynchronizationManager.getSynchronizations().size() == 1);

		TransactionSynchronizationManager.clearSynchronization();
		TransactionSynchronizationManager.unbindResource(factory);

		EasyMock.verify(session);
		EasyMock.verify(methodInvocation);
	}

}
