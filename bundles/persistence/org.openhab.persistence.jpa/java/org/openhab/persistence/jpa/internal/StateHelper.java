/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.persistence.jpa.internal;

import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.State;

/**
 * Helper class for dealing with State
 * @author mbergmann
 *
 */
public class StateHelper {

	/**
	 * Converts the given State to a string that can be persisted in db
	 * @param state the state of the item to be persisted
	 * @return state converted as string
	 * @throws Exception
	 */
	static public String toString(State state) throws Exception {
		if(state instanceof DateTimeType) {
			return String.valueOf(((DateTimeType)state).getCalendar().getTime().getTime());
		}
		if(state instanceof DecimalType) {
			return String.valueOf(((DecimalType)state).doubleValue());
		}

		return state.toString();
	}	
}
