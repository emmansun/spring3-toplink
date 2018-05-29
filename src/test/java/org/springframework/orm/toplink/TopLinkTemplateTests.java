/*
 * Created on Mar 20, 2005
 *
 */

package org.springframework.orm.toplink;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import oracle.toplink.exceptions.TopLinkException;
import oracle.toplink.sessions.Session;

import org.easymock.EasyMock;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Juergen Hoeller
 * @author <a href="mailto:james.x.clark@oracle.com">James Clark</a>
 * @since 28.04.2005
 */
public class TopLinkTemplateTests {

	@Test
	public void testTemplateNotAllowingCreate() {
		Session session = EasyMock.createNiceMock(Session.class);

		SessionFactory factory = new SingleSessionFactory(session);

		TopLinkTemplate template = new TopLinkTemplate();
		template.setAllowCreate(false);
		template.setSessionFactory(factory);
		try {
			template.execute(new TopLinkCallback<Object>() {
				public Object doInTopLink(Session session)
						throws TopLinkException {
				return null;
				}
			});
			fail();
		}
		catch (Exception e) {
		}
	}

	@Test
	public void testTemplateWithCreate() {
		Session session = EasyMock.createNiceMock(Session.class);

		SessionFactory factory = new SingleSessionFactory(session);

		session.release();
		EasyMock.expectLastCall().times(1);
		EasyMock.replay(session);

		TopLinkTemplate template = new TopLinkTemplate();
		template.setAllowCreate(true);
		template.setSessionFactory(factory);
		template.execute(new TopLinkCallback<Object>() {
			public Object doInTopLink(Session session) throws TopLinkException {
				assertTrue(session != null);
				return null;
			}
		});
		assertFalse(TransactionSynchronizationManager.hasResource(factory));

		EasyMock.verify(session);
	}

	@Test
	public void testTemplateWithExistingSessionAndNoCreate() {
		Session session = EasyMock.createNiceMock(Session.class);

		SessionFactory factory = new SingleSessionFactory(session);

		EasyMock.replay(session);

		SessionHolder sessionHolder = new SessionHolder(factory.createSession());
		TransactionSynchronizationManager.bindResource(factory, sessionHolder);

		TopLinkTemplate template = new TopLinkTemplate();
		template.setAllowCreate(false);
		template.setSessionFactory(factory);
		template.execute(new TopLinkCallback<Object>() {
			public Object doInTopLink(Session session) throws TopLinkException {
				assertTrue(session != null);
				return null;
			}
		});
		assertTrue(TransactionSynchronizationManager.hasResource(factory));
		EasyMock.verify(session);
		TransactionSynchronizationManager.unbindResource(factory);
	}

	@Test
	public void testTemplateWithExistingSessionAndCreateAllowed() {
		Session session = EasyMock.createNiceMock(Session.class);

		SessionFactory factory = new SingleSessionFactory(session);

		EasyMock.replay(session);

		SessionHolder sessionHolder = new SessionHolder(factory.createSession());
		TransactionSynchronizationManager.bindResource(factory, sessionHolder);

		TopLinkTemplate template = new TopLinkTemplate();
		template.setAllowCreate(true);
		template.setSessionFactory(factory);
		template.execute(new TopLinkCallback<Object>() {
			public Object doInTopLink(Session session) throws TopLinkException {
				assertTrue(session != null);
				return null;
			}
		});
		assertTrue(TransactionSynchronizationManager.hasResource(factory));
		EasyMock.verify(session);
		TransactionSynchronizationManager.unbindResource(factory);
	}
}
