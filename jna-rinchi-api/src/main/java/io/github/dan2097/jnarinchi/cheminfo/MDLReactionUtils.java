package io.github.dan2097.jnarinchi.cheminfo;

import java.text.NumberFormat;
import java.util.Locale;

import io.github.dan2097.jnainchi.InchiAtom;

public class MDLReactionUtils {
	public static final int MDL_FLOAT_SPACES = 10;
	public static final String CTAB_LINE_COUNT = "999";
	public static final NumberFormat MDL_NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.ENGLISH);
	
	static {
		MDL_NUMBER_FORMAT.setMinimumIntegerDigits(1);
		MDL_NUMBER_FORMAT.setMaximumIntegerDigits(4);
		MDL_NUMBER_FORMAT.setMinimumFractionDigits(4);
		MDL_NUMBER_FORMAT.setMaximumFractionDigits(4);
		MDL_NUMBER_FORMAT.setGroupingUsed(false);
	}
	
	public static int getImplicitHAtomCoding(InchiAtom atom) {
		//Implicit H atoms coding: 1 = H0, 2 = H1, 3 = H2, 4 = H3, 5 = H4
		if (atom.getImplicitHydrogen() == 0)
			return 0;
		else
			return atom.getImplicitHydrogen() + 1; 
	}
}
