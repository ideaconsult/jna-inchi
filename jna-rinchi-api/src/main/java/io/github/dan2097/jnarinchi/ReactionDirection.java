/**
 * JNA-RInChI - Library for calling RInChI from Java
 * Copyright © 2022 Nikolay Kochev
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.dan2097.jnarinchi;

public enum ReactionDirection 
{	
	FORWARD ("+"), 
	BACKWARD ("-"), 
	EQUILIBRIUM ("=");
	
	private final String shortRinchiDeignation;
	
	private ReactionDirection(String shortRinchiDeignation) {
		this.shortRinchiDeignation = shortRinchiDeignation;
	}

	public String getShortRinchiDeignation() {
		return shortRinchiDeignation;
	}
	
	public static ReactionDirection getDirectionFromShortDesignation(String designation) {
		for (ReactionDirection dir : ReactionDirection.values())
			if (dir.getShortRinchiDeignation().equals(designation))
				return dir;
		return null;
	}
	
}