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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Status;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import oracle.toplink.exceptions.TopLinkException;
import oracle.toplink.sessions.Session;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;
import org.springframework.transaction.MockJtaTransaction;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Juergen Hoeller
 * @author <a href="mailto:james.x.clark@oracle.com">James Clark</a>
 * @since 28.04.2005
 */
public class TopLinkJtaTransactionTests {

	@Test
	public void testParticipatingJtaTransactionWithWithRequiresNew() throws Exception {
		UserTransaction ut = EasyMock.createNiceMock(UserTransaction.class);
		TransactionManager tm = EasyMock.createNiceMock(TransactionManager.class);
		javax.transaction.Transaction tx1 = EasyMock.createNiceMock(javax.transaction.Transaction.class);

		Session session1 = EasyMock.createNiceMock(Session.class);
		final Session session2 = EasyMock.createNiceMock(Session.class);
		final MockSessionFactory sf = new MockSessionFactory(session1);

		EasyMock.expect(ut.getStatus()).andReturn(Status.STATUS_NO_TRANSACTION).times(1);
		EasyMock.expect(ut.getStatus()).andReturn(Status.STATUS_ACTIVE).times(5);
		ut.begin();
		EasyMock.expectLastCall().times(2);
		EasyMock.expect(tm.suspend()).andReturn(tx1).times(1);
		tm.resume(tx1);
		EasyMock.expectLastCall().times(1);
		ut.commit();
		EasyMock.expectLastCall().times(2);

		session1.release();
		EasyMock.expectLastCall().times(1);

		session2.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(ut);
		EasyMock.replay(tm);
		EasyMock.replay(session1);
		EasyMock.replay(session2);

		JtaTransactionManager ptm = new JtaTransactionManager();
		ptm.setUserTransaction(ut);
		ptm.setTransactionManager(tm);
		final TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				SessionFactoryUtils.getSession(sf, true);
				final SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(sf);
				assertTrue("Has thread session", holder != null);
				sf.setSession(session2);

				tt.execute(new TransactionCallback() {
					public Object doInTransaction(TransactionStatus status) {
						TopLinkTemplate ht = new TopLinkTemplate(sf);
						return ht.executeFind(new TopLinkCallback() {
							public Object doInTopLink(Session session) {
								assertTrue("Not enclosing session", session != holder.getSession());
								return null;
							}
						});
					}
				});
				assertTrue("Same thread session as before",
						holder.getSession() == SessionFactoryUtils.getSession(sf, false));
				return null;
			}
		});
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));

		EasyMock.verify(ut);
		EasyMock.verify(tm);
		EasyMock.verify(session1);
		EasyMock.verify(session2);
	}

	@Test
	public void testJtaTransactionCommit() throws Exception {
		doTestJtaTransactionCommit(Status.STATUS_NO_TRANSACTION);
	}

	@Test
	public void testJtaTransactionCommitWithExisting() throws Exception {
		doTestJtaTransactionCommit(Status.STATUS_ACTIVE);
	}

	private void doTestJtaTransactionCommit(int status) throws Exception {
		UserTransaction ut = EasyMock.createNiceMock(UserTransaction.class);
		EasyMock.expect(ut.getStatus()).andReturn(status).times(1);
		if (status == Status.STATUS_NO_TRANSACTION) {
			ut.begin();
			EasyMock.expectLastCall().times(1);
			EasyMock.expect(ut.getStatus()).andReturn(Status.STATUS_ACTIVE).times(1);
			ut.commit();
			EasyMock.expectLastCall().times(1);
		}
		else {
			EasyMock.expect(ut.getStatus()).andReturn(status).times(1);
		}
		EasyMock.replay(ut);

		final Session session = EasyMock.createNiceMock(Session.class);
		final SessionFactory sf = new SingleSessionFactory(session);

		EasyMock.replay(session);

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		final List l = new ArrayList();
		l.add("test");
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));

		Object result = tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				try {
					assertTrue("JTA synchronizations active", TransactionSynchronizationManager.isSynchronizationActive());
					assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
					TopLinkTemplate ht = new TopLinkTemplate(sf);
					List htl = ht.executeFind(new TopLinkCallback() {
						public Object doInTopLink(Session sess) {
							assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
							assertEquals(session, sess);
							return l;
						}
					});

					ht = new TopLinkTemplate(sf);
					htl = ht.executeFind(new TopLinkCallback() {
						public Object doInTopLink(Session sess) {
							assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
							assertEquals(session, sess);
							return l;
						}
					});
					assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));

					EasyMock.verify(session);
					EasyMock.reset(session);

					try {
						session.release();
						EasyMock.expectLastCall().times(1);
					}
					catch (TopLinkException e) {
					}
					EasyMock.replay(session);
					return htl;
				}
				catch (Error err) {
					err.printStackTrace();
					throw err;
				}
			}
		});

		assertTrue("Correct result list", result == l);
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());

		EasyMock.verify(ut);
		EasyMock.verify(session);
	}

	@Test
	public void testJtaTransactionCommitWithJtaTm() throws Exception {
		doTestJtaTransactionCommitWithJtaTm(Status.STATUS_NO_TRANSACTION);
	}

	@Test
	public void testJtaTransactionCommitWithJtaTmAndExisting() throws Exception {
		doTestJtaTransactionCommitWithJtaTm(Status.STATUS_ACTIVE);
	}

	private void doTestJtaTransactionCommitWithJtaTm(int status) throws Exception {
		UserTransaction ut = EasyMock.createNiceMock(UserTransaction.class);
		EasyMock.expect(ut.getStatus()).andReturn(status).times(1);
		if (status == Status.STATUS_NO_TRANSACTION) {
			ut.begin();
			EasyMock.expectLastCall().times(1);
			EasyMock.expect(ut.getStatus()).andReturn(Status.STATUS_ACTIVE).times(2);
			ut.commit();
			EasyMock.expectLastCall().times(1);
		}
		else {
			EasyMock.expect(ut.getStatus()).andReturn(status).times(1);
		}

		TransactionManager tm = EasyMock.createNiceMock(TransactionManager.class);
		MockJtaTransaction transaction = new MockJtaTransaction();
		EasyMock.expect(tm.getStatus()).andReturn(Status.STATUS_ACTIVE).times(6);
		EasyMock.expect(tm.getTransaction()).andReturn(transaction).times(6);

		final Session session = EasyMock.createNiceMock(Session.class);
		final SessionFactory sf = new SingleSessionFactory(session);

		EasyMock.replay(ut);
		EasyMock.replay(tm);
		EasyMock.replay(session);

		JtaTransactionManager ptm = new JtaTransactionManager(ut);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		final List l = new ArrayList();
		l.add("test");
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));

		Object result = tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				try {
					assertTrue("JTA synchronizations active", TransactionSynchronizationManager.isSynchronizationActive());
					assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));

					TopLinkTemplate ht = new TopLinkTemplate(sf);
					List htl = ht.executeFind(new TopLinkCallback() {
						public Object doInTopLink(Session sess) {
							assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
							assertEquals(session, sess);
							return l;
						}
					});

					ht = new TopLinkTemplate(sf);
					htl = ht.executeFind(new TopLinkCallback() {
						public Object doInTopLink(Session sess) {
							assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
							assertEquals(session, sess);
							return l;
						}
					});

					assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
					EasyMock.verify(session);
					EasyMock.reset(session);
					try {
						session.release();
						EasyMock.expectLastCall().times(1);
					}
					catch (TopLinkException e) {
					}
					EasyMock.replay(session);
					return htl;
				}
				catch (Error err) {
					err.printStackTrace();
					throw err;
				}
			}
		});

		assertTrue("Correct result list", result == l);
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());

		EasyMock.verify(ut);
		EasyMock.verify(session);
	}

	@After
	public void tearDown() {
		assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
		assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
	}

}
