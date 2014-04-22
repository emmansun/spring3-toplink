/*
 * Created on Mar 20, 2005
 *
 */

package org.springframework.orm.toplink;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import oracle.toplink.sessions.Session;
import oracle.toplink.sessions.UnitOfWork;

import org.easymock.EasyMock;
import org.junit.Test;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Juergen Hoeller
 * @author <a href="mailto:james.x.clark@oracle.com">James Clark</a>
 * @since 28.04.2005
 */
public class TopLinkTransactionManagerTests {
	@Test
	public void testTransactionCommit() {
		Session session = EasyMock.createNiceMock(Session.class);
		UnitOfWork uow = EasyMock.createNiceMock(UnitOfWork.class);

		final SessionFactory sf = new MockSessionFactory(session);

		// during commit, TM must get the active UnitOfWork
		EasyMock.expect(session.getActiveUnitOfWork()).andReturn(uow).times(2);
		uow.beginEarlyTransaction();
		EasyMock.expectLastCall().times(1);
		uow.commit();
		EasyMock.expectLastCall().times(1);
		// session should be released when it was bound explicitly by the TM
		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session);
		EasyMock.replay(uow);

		TopLinkTransactionManager tm = new TopLinkTransactionManager();
		tm.setJdbcExceptionTranslator(new SQLStateSQLExceptionTranslator());
		tm.setSessionFactory(sf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
		tt.setTimeout(10);
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());

		Object result = tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
				TopLinkTemplate template = new TopLinkTemplate(sf);
				return template.execute(new TopLinkCallback() {
					public Object doInTopLink(Session session) {
						return null;
					}
				});
			}
		});
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());
		EasyMock.verify(session);
		EasyMock.verify(uow);
	}

	@Test
	public void testTransactionRollback() {
		Session session = EasyMock.createNiceMock(Session.class);
		UnitOfWork uow = EasyMock.createNiceMock(UnitOfWork.class);

		final SessionFactory sf = new MockSessionFactory(session);

		EasyMock.expect(session.getActiveUnitOfWork()).andReturn(uow).times(1);
		uow.beginEarlyTransaction();
		EasyMock.expectLastCall().times(1);
		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session);
		EasyMock.replay(uow);

		TopLinkTransactionManager tm = new TopLinkTransactionManager();
		tm.setSessionFactory(sf);
		tm.setJdbcExceptionTranslator(new SQLStateSQLExceptionTranslator());
		TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
		tt.setTimeout(10);
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());

		try {
			Object result = tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
					TopLinkTemplate template = new TopLinkTemplate(sf);
					return template.execute(new TopLinkCallback() {
						public Object doInTopLink(Session session) {
							throw new RuntimeException("failure");
						}
					});
				}
			});
			fail("Should have propagated RuntimeException");
		}
		catch (RuntimeException ex) {
			assertTrue(ex.getMessage().equals("failure"));
		}
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());

		EasyMock.verify(session);
		EasyMock.verify(uow);
	}

	@Test
	public void testTransactionRollbackOnly() {
		Session session = EasyMock.createNiceMock(Session.class);

		final SessionFactory sf = new MockSessionFactory(session);
		session.release();
		EasyMock.expectLastCall().times(1);
		EasyMock.replay(session);

		TopLinkTransactionManager tm = new TopLinkTransactionManager();
		tm.setSessionFactory(sf);
		tm.setLazyDatabaseTransaction(true);
		tm.setJdbcExceptionTranslator(new SQLStateSQLExceptionTranslator());
		TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
		tt.setTimeout(10);
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());

		Object result = tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				assertTrue("Has thread session",
						TransactionSynchronizationManager.hasResource(sf));
				TopLinkTemplate template = new TopLinkTemplate(sf);
				template.execute(new TopLinkCallback() {
					public Object doInTopLink(Session session) {
						return null;
					}
				});
				status.setRollbackOnly();
				return null;
			}
		});
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());
		EasyMock.verify(session);
	}

	@Test
	public void testParticipatingTransactionWithCommit() {
		final Session session = EasyMock.createNiceMock(Session.class);
		UnitOfWork uow = EasyMock.createNiceMock(UnitOfWork.class);

		final SessionFactory sf = new MockSessionFactory(session);

		EasyMock.expect(session.getActiveUnitOfWork()).andReturn(uow).times(2);
		uow.beginEarlyTransaction();
		EasyMock.expectLastCall().times(1);
		uow.commit();
		EasyMock.expectLastCall().times(1);
		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session);
		EasyMock.replay(uow);

		PlatformTransactionManager tm = new TopLinkTransactionManager(sf);
		final TransactionTemplate tt = new TransactionTemplate(tm);

		Object result = tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				return tt.execute(new TransactionCallback() {
					public Object doInTransaction(TransactionStatus status) {
						TopLinkTemplate ht = new TopLinkTemplate(sf);
						return ht.executeFind(new TopLinkCallback() {
							public Object doInTopLink(Session injectedSession) {
								assertTrue(session == injectedSession);
								return null;
							}
						});
					}
				});
			}
		});

		EasyMock.verify(session);
		EasyMock.verify(uow);
	}

	@Test
	public void testParticipatingTransactionWithRollback() {
		final Session session = EasyMock.createNiceMock(Session.class);

		final SessionFactory sf = new MockSessionFactory(session);

		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session);

		TopLinkTransactionManager tm = new TopLinkTransactionManager(sf);
		tm.setLazyDatabaseTransaction(true);
		final TransactionTemplate tt = new TransactionTemplate(tm);
		try {
			tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					return tt.execute(new TransactionCallback() {
						public Object doInTransaction(TransactionStatus status) {
							TopLinkTemplate ht = new TopLinkTemplate(sf);
							return ht.executeFind(new TopLinkCallback() {
								public Object doInTopLink(Session session) {
									throw new RuntimeException("application exception");
								}
							});
						}
					});
				}
			});
			fail("Should not thrown RuntimeException");
		}
		catch (RuntimeException ex) {
			assertTrue(ex.getMessage().equals("application exception"));
		}
		EasyMock.verify(session);
	}

	@Test
	public void testParticipatingTransactionWithRollbackOnly() {
		final Session session = EasyMock.createNiceMock(Session.class);
		final SessionFactory sf = new MockSessionFactory(session);

		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session);

		TopLinkTransactionManager tm = new TopLinkTransactionManager(sf);
		tm.setLazyDatabaseTransaction(true);
		final TransactionTemplate tt = new TransactionTemplate(tm);

		try {
			tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					tt.execute(new TransactionCallback() {
						public Object doInTransaction(TransactionStatus status) {
							TopLinkTemplate ht = new TopLinkTemplate(sf);
							ht.execute(new TopLinkCallback() {
								public Object doInTopLink(Session session) {
									return null;
								}
							});
							status.setRollbackOnly();
							return null;
						}
					});
					return null;
				}
			});
			fail("Should have thrown UnexpectedRollbackException");
		}
		catch (UnexpectedRollbackException ex) {
			// expected
		}

		EasyMock.verify(session);
	}

	@Test
	public void testParticipatingTransactionWithWithRequiresNew() {
		final Session session1 = EasyMock.createNiceMock(Session.class);
		final Session session2 = EasyMock.createNiceMock(Session.class);
		final UnitOfWork uow1 = EasyMock.createNiceMock(UnitOfWork.class);
		final UnitOfWork uow2 = EasyMock.createNiceMock(UnitOfWork.class);

		final MockSessionFactory sf = new MockSessionFactory(session1);

		EasyMock.expect(session2.getActiveUnitOfWork()).andReturn(uow2).times(2);
		
		uow2.beginEarlyTransaction();
		EasyMock.expectLastCall().times(1);
		
		uow2.commit();
		EasyMock.expectLastCall().times(1);
		
		session2.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.expect(session1.getActiveUnitOfWork()).andReturn(uow1).times(2);
		
		uow1.beginEarlyTransaction();
		EasyMock.expectLastCall().times(1);
		uow1.commit();
		EasyMock.expectLastCall().times(1);
		session1.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session1);
		EasyMock.replay(session2);
		EasyMock.replay(uow1);
		EasyMock.replay(uow2);

		PlatformTransactionManager tm = new TopLinkTransactionManager(sf);
		final TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				final SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(sf);
				assertTrue("Has thread session", holder != null);
				sf.setSession(session2);
				tt.execute(new TransactionCallback() {
					public Object doInTransaction(TransactionStatus status) {
						TopLinkTemplate ht = new TopLinkTemplate(sf);
						return ht.execute(new TopLinkCallback() {
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

		EasyMock.verify(session1);
		EasyMock.verify(session2);
		EasyMock.verify(uow1);
		EasyMock.verify(uow2);
	}

	@Test
	public void testParticipatingTransactionWithWithNotSupported() {
		final Session session = EasyMock.createNiceMock(Session.class);
		final UnitOfWork uow = EasyMock.createNiceMock(UnitOfWork.class);
		
		final SessionFactory sf = new MockSessionFactory(session);

		EasyMock.expect(session.getActiveUnitOfWork()).andReturn(uow).times(2);
		uow.beginEarlyTransaction();
		EasyMock.expectLastCall().times(1);
		uow.commit();
		EasyMock.expectLastCall().times(1);
		session.release();
		EasyMock.expectLastCall().times(2);

		EasyMock.replay(session);
		EasyMock.replay(uow);

		TopLinkTransactionManager tm = new TopLinkTransactionManager(sf);
		final TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(sf);
				assertTrue("Has thread session", holder != null);
				tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
				tt.execute(new TransactionCallback() {
					public Object doInTransaction(TransactionStatus status) {
						assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
						TopLinkTemplate ht = new TopLinkTemplate(sf);

						return ht.execute(new TopLinkCallback() {
							public Object doInTopLink(Session session) {
								return null;
							}
						});
					}
				});
				assertTrue("Same thread session as before", holder.getSession() == SessionFactoryUtils.getSession(sf, false));
				return null;
			}
		});
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));

		EasyMock.verify(session);
		EasyMock.verify(uow);
	}

	@Test
	public void testTransactionWithPropagationSupports() {
		final Session session = EasyMock.createNiceMock(Session.class);

		final SessionFactory sf = new MockSessionFactory(session);

		// not a new transaction, won't start a new one
		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session);

		PlatformTransactionManager tm = new TopLinkTransactionManager(sf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));

		tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
				assertTrue("Is not new transaction", !status.isNewTransaction());
				TopLinkTemplate ht = new TopLinkTemplate(sf);
				ht.execute(new TopLinkCallback() {
					public Object doInTopLink(Session session) {
						return null;
					}
				});
				assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
				return null;
			}
		});

		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		EasyMock.verify(session);
	}

	@Test
	public void testTransactionCommitWithReadOnly() {
		final Session session = EasyMock.createNiceMock(Session.class);
		final UnitOfWork uow = EasyMock.createNiceMock(UnitOfWork.class);

		final SessionFactory sf = new MockSessionFactory(session);

		session.release();
		EasyMock.expectLastCall().times(1);

		EasyMock.replay(session);
		EasyMock.replay(uow);

		TopLinkTransactionManager tm = new TopLinkTransactionManager(sf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setReadOnly(true);
		final List l = new ArrayList();
		l.add("test");
		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());

		Object result = tt.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				assertTrue("Has thread session", TransactionSynchronizationManager.hasResource(sf));
				TopLinkTemplate ht = new TopLinkTemplate(sf);
				return ht.executeFind(new TopLinkCallback() {
					public Object doInTopLink(Session session) {
						return l;
					}
				});
			}
		});
		assertTrue("Correct result list", result == l);

		assertTrue("Hasn't thread session", !TransactionSynchronizationManager.hasResource(sf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isSynchronizationActive());
		EasyMock.verify(session);
		EasyMock.verify(uow);
	}

	protected void tearDown() {
		assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
		assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
		assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
	}

}
