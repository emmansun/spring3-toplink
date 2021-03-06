/*
 * Copyright 2002-2006 the original author or authors.
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

import oracle.toplink.exceptions.OptimisticLockException;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * TopLink-specific subclass of ObjectOptimisticLockingFailureException.
 * Converts TopLink's OptimisticLockException.
 *
 * @author Juergen Hoeller
 * @since Spring framework 1.2
 */
@SuppressWarnings("serial")
public class TopLinkOptimisticLockingFailureException extends ObjectOptimisticLockingFailureException {

	public TopLinkOptimisticLockingFailureException(OptimisticLockException ex) {
		super(ex.getObject() != null ? ex.getObject().getClass() : null, null, ex.getMessage(), ex);
	}

}
