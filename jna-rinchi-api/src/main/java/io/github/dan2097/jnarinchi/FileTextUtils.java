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

import java.io.BufferedReader;
import java.io.StringReader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.dan2097.jnainchi.InchiAtom;
import io.github.dan2097.jnainchi.InchiBond;
import io.github.dan2097.jnainchi.InchiBondType;
import io.github.dan2097.jnarinchi.cheminfo.MoleculeUtils;
import io.github.dan2097.jnarinchi.cheminfo.PerTable;

public class FileTextUtils {
	
	public static enum CTABVersion {
		V2000, V3000
	}
	
	private static NumberFormat mdlNumberFormat = NumberFormat.getNumberInstance(Locale.ENGLISH);
	private static final int MDL_FLOAT_SPACES = 10;
	
	static {
		mdlNumberFormat.setMinimumIntegerDigits(1);
		mdlNumberFormat.setMaximumIntegerDigits(4);
		mdlNumberFormat.setMinimumFractionDigits(4);
		mdlNumberFormat.setMaximumFractionDigits(4);
		mdlNumberFormat.setGroupingUsed(false);
	}
	
	private String endLine = "\n";	
	private StringBuilder strBuilder;	
	private List<String> errors = new ArrayList<String>();
	private ReactionFileFormat format = ReactionFileFormat.RD;
	private boolean add_M_ISO_Line = false;
	private boolean add_M_CHG_Line = false;
	private CTABVersion ctabVersion = CTABVersion.V2000; //Currently only V2000 is supported
	private RinchiInput rInput = null;
	private List<RinchiInputComponent> reagents = new ArrayList<RinchiInputComponent>();
	private List<RinchiInputComponent> products = new ArrayList<RinchiInputComponent>();
	private List<RinchiInputComponent> agents = new ArrayList<RinchiInputComponent>();
	
	//Reading work variables
	private String inputString = null;
	private BufferedReader inputReader = null;
	private int curLineNum = 0;
	private int numOfReagentsToRead = 0;
	private int numOfProductsToRead = 0;
	private int numOfAtomsToRead = 0;
	private int numOfBondsToRead = 0;
	private String errorComponentContext = "";
	
		
	public RinchiInput fileTextToRinchiInput(String inputString) {
		this.inputString = inputString;
		BufferedReader reader = new BufferedReader(new StringReader(inputString));
		return fileTextToRinchiInput(reader);
	}
	
	public RinchiInput fileTextToRinchiInput(BufferedReader inputReader) {
		this.inputReader = inputReader;
		resetForFileTextReading();
		rInput = new RinchiInput();
		
		try {
			iterateInputLines();
			inputReader.close();
		}
		catch (Exception x) {
			errors.add("Error on reading or closing input reader: " + x.getMessage());
		}
		
		if (errors.isEmpty())
			return rInput;
		else
			return null;
	}
	
	public String rinchiInputToFileText(RinchiInput rInp) {
		this.rInput = rInp;
		if (rInput == null) {
			errors.add("RinchiInput is null!");
			return null;
		}
		
		reset();
		analyzeComponents();
		
		if (format == ReactionFileFormat.RD || format == ReactionFileFormat.AUTO )
			addRDFileHeader("1", "JNA-RIN", "");
		
		addRXNHeader("Reaction 1", "      JNA-RIN", "");
		
		//Add RXN count line: rrrppp
		addInteger(reagents.size(), 3); //rrr
		addInteger(products.size(), 3); //ppp
		strBuilder.append(endLine);
		
		//Add reagents
		for (int i = 0; i < reagents.size(); i++) 
			addRrinchiInputComponent(reagents.get(i), "Reagent " + (i+1), "  JNA-RIN", "");
		//Add products
		for (int i = 0; i < products.size(); i++) 
			addRrinchiInputComponent(products.get(i), "Products " + (i+1), "  JNA-RIN", "");
		
		
		//TODO add agents for RDFile
		
		return strBuilder.toString();
	}
	
	private void reset() {		
		strBuilder = new StringBuilder();
		errors.clear();
		reagents.clear();
		products.clear();
		agents.clear();
	}
	
	private void resetForFileTextReading() {
		errors.clear();
		reagents.clear();
		products.clear();
		agents.clear();
		curLineNum = 0;
		numOfReagentsToRead = 0;
		numOfProductsToRead = 0;
	}
	
	private void addRrinchiInputComponent(RinchiInputComponent ric, String line1, String line2, String line3) 
	{
		addMolHeader(line1, line2, line3);
		addCTABBlockV2000(ric);
		
		strBuilder.append("M  END");
		strBuilder.append(endLine);
	}
	
	private void addMolHeader(String line1, String line2, String line3) {
		strBuilder.append("$MOL");
		strBuilder.append(endLine);
		strBuilder.append(line1);
		strBuilder.append(endLine);		
		strBuilder.append(line2);
		strBuilder.append(endLine);
		strBuilder.append(line3);
		strBuilder.append(endLine);
	}
	
	private void addRDFileHeader(String info1, String info2, String info3) {
		strBuilder.append("$RDFILE ");
		strBuilder.append(info1);
		strBuilder.append(endLine);
		strBuilder.append("$DATM ");
		strBuilder.append(info2);
		strBuilder.append(endLine);
		strBuilder.append("$RFMT ");		
		strBuilder.append(info3);
		strBuilder.append(endLine);
	}
	
	private void addRXNHeader(String line2, String line3, String line4) {
		strBuilder.append("$RXN");
		strBuilder.append(endLine);
		strBuilder.append(line2);
		strBuilder.append(endLine);
		strBuilder.append(line3);
		strBuilder.append(endLine);		
		strBuilder.append(line4);
		strBuilder.append(endLine);
	}
	
	private void addCTABBlockV2000(RinchiInputComponent ric) {
		//Counts line: aaabbblllfffcccsssxxxrrrpppiiimmmvvvvvv
		addInteger(ric.getAtoms().size(), 3); //aaa
		addInteger(ric.getBonds().size(), 3); //bbb
		strBuilder.append("  0"); //lll
		strBuilder.append("  0"); //fff
		addInteger(ric.getStereos().size(), 3); //ccc
		strBuilder.append("  0"); //sss
		strBuilder.append("  0"); //xxx
		strBuilder.append("  0"); //rrr
		strBuilder.append("  0"); //ppp
		strBuilder.append("  0"); //iii
		strBuilder.append("999"); //mmm
		strBuilder.append(" V2000"); //vvvvvv
		strBuilder.append(endLine);
		
		//Add Atom block
		for (int i = 0; i < ric.getAtoms().size(); i++) 
			addAtomLine(ric.getAtom(i));
		//Add Bond block
		for (int i = 0; i < ric.getBonds().size(); i++) 
			addBondLine(ric.getBond(i), ric);
	}
	
	private void addAtomLine(InchiAtom atom) {
		//MDL atom line specification
		//xxxxx.xxxxyyyyy.yyyyzzzzz.zzzz aaaddcccssshhhbbbvvvHHHrrriiimmmnnneee
		
		//x,y,z coordinates
		addDouble(atom.getX());
		addDouble(atom.getY());
		addDouble(atom.getZ());
		strBuilder.append(" ");
		//aaa
		addString(atom.getElName(),3); 
		//dd not specified yet
		strBuilder.append(" 0"); 
		//ccc
		addInteger(getOldCTABChargeCoding(atom.getCharge()),3);
		//sss stereoparity not specified yet - TODO
		strBuilder.append("  0"); 
		//hhh: implicit H atoms: used for query 
		//addInteger(getImplicitHAtomCoding(atom),3);
		strBuilder.append("  0");
		//bbb stereo box care: used for queries
		strBuilder.append("  0");
		//vvv valence
		strBuilder.append("  0");
		//HHH not specified
		strBuilder.append("  0");
		
		//rrriiimmmnnneee are not specified
		strBuilder.append(endLine);
	}
	
	private void addBondLine(InchiBond bond, RinchiInputComponent ric) {
		//MDL bond line specification
		//111222tttsssxxxrrrccc
		
		//111 firts atom
		int firstAt = ric.getAtoms().indexOf(bond.getStart()) + 1; //1-based atom numbering
		addInteger(firstAt, 3);
		//222 second atom
		int secondAt = ric.getAtoms().indexOf(bond.getEnd()) + 1; //1-based atom numbering
		addInteger(secondAt, 3);
		//ttt bond type
		addInteger(getBondMDLBondCode(bond), 3);
		//sss bond stereo - not specified - TODO
		strBuilder.append("  0");
		//xxx = not used
		strBuilder.append("  0");
		//rrr (bond topology, used only for SSS)
		strBuilder.append("  0");
		//ccc (reacting center status): 0 - unmarked 
		strBuilder.append("  0");
		strBuilder.append(endLine);
	}
	
	int getBondMDLBondCode(InchiBond bond) {
		switch (bond.getType()) {
		case SINGLE:
			return 1;
		case DOUBLE:
			return 2;
		case TRIPLE:
			return 3;
		};
		return 1;
	}
	
	private void analyzeComponents() {
		for (RinchiInputComponent ric : rInput.getComponents()) {
			switch (ric.getRole()) {
			case REAGENT:
				reagents.add(ric);
				break;
			case PRODUCT:
				products.add(ric);
				break;
			case AGENT:
				agents.add(ric);
				break;
			}
		}
	}
	
	private void addString(String vStr, int fixedSpace) {
		addString(vStr, fixedSpace, true);
	}
	
	private void addString(String vStr, int fixedSpace, boolean spacesAtTheEnd) {
		//Adding empty spaces and value
		int nEmptySpaces = fixedSpace - vStr.length();
		if (nEmptySpaces < 0) 
			strBuilder.append(vStr.substring(fixedSpace));
		else {
			if (spacesAtTheEnd) {
				strBuilder.append(vStr);
				for (int i = 0; i < nEmptySpaces; i++)
					strBuilder.append(" ");
			} else {
				for (int i = 0; i < nEmptySpaces; i++)
					strBuilder.append(" ");
				strBuilder.append(vStr);
			}
		}
	}
	
	private void addInteger(int value, int fixedSpace) {
		String vStr = Integer.toString(value);
		if (vStr.length() > fixedSpace)
			vStr = "0";
		//Adding empty spaces and value
		int nEmptySpaces = fixedSpace - vStr.length();
		for (int i = 0; i < nEmptySpaces; i++)
			strBuilder.append(" ");
		strBuilder.append(vStr);
	}
	
	private void addDouble(Double value) {
		addDouble(value, mdlNumberFormat, MDL_FLOAT_SPACES);
	}
	
	private void addDouble(Double value, NumberFormat nf, int fixedSpace) {
		String vStr;
		if(Double.isNaN(value) || Double.isInfinite(value))
			vStr = nf.format(0.0);
		else
			vStr = nf.format(value);
		
		if (vStr.length() > fixedSpace)
			vStr = "0";
		//Adding empty spaces and value
		int nEmptySpaces = fixedSpace - vStr.length();
		for (int i = 0; i < nEmptySpaces; i++)
			strBuilder.append(" ");
		strBuilder.append(vStr);
	}
	
	
	private int getOldCTABChargeCoding(int charge) {
		//MDL Charge designation/coding
		//0 = uncharged or value other than these, 1 = +3, 2 = +2, 3 = +1,
		//4 = doublet radical, 5 = -1, 6 = -2, 7 = -3
		switch (charge) {
		case +3:
			return 1;
		case +2:
			return 2;
		case +1:
			return 1;
		case -1:
			return 5;
		case -2:
			return 6;
		case -3:
			return 7;
		}
		return 0;
	}
	
	private int getChargeFromOldCTABCoding(int code) {
		//MDL Charge designation/coding
		//0 = uncharged or value other than these, 1 = +3, 2 = +2, 3 = +1,
		//4 = doublet radical, 5 = -1, 6 = -2, 7 = -3
		switch (code) {
		case 1:
			return +3;
		case 2:
			return +2;
		case 3:
			return +1;
		case 5:
			return -1;
		case 6:
			return -2;
		case 7:
			return -3;
		}
		return 0;
	}
	
	private int getImplicitHAtomCoding(InchiAtom atom) {
		//Implicit H atoms coding: 1 = H0, 2 = H1, 3 = H2, 4 = H3, 5 = H4
		if (atom.getImplicitHydrogen() == 0)
			return 0;
		else
			return atom.getImplicitHydrogen() + 1; 
	}
			
	private String readLine() {
		String line = null;
		curLineNum++;
		try {
			line = inputReader.readLine();
		}
		catch (Exception x) {
			errors.add("Unable to read line " + curLineNum + ": " + x.getMessage());
		}
		return line; 
	}
	
	private int iterateInputLines() {
		//Handle file header/headers according to format
		switch(format) {
		case AUTO:
			readAutoFileHeader();
			break;
		case RXN:
			readRXNFileHeader();
			break;
		case RD:
			readRDFileHeader();
			break;	
		}		
		if (!errors.isEmpty())
			return errors.size();
		
		readRXNCountLine();
		if (!errors.isEmpty())
			return errors.size();
		
		//Reading reagents
		for (int i = 0; i< numOfReagentsToRead; i++) {
			RinchiInputComponent ric = readMDLMolecule();
			errorComponentContext = "Reading reagent #" + (i+1) + " ";
			if (ric != null) {
				MoleculeUtils.setImplicitHydrogenAtoms(ric);
				ric.setRole(ReactionComponentRole.REAGENT);
				rInput.addComponent(ric);
			}
			else
				return errors.size();
		}
		
		//Reading products
		for (int i = 0; i< numOfProductsToRead; i++) {
			RinchiInputComponent ric = readMDLMolecule();
			errorComponentContext = "Reading product #" + (i+1) + " ";
			if (ric != null) {
				MoleculeUtils.setImplicitHydrogenAtoms(ric);
				ric.setRole(ReactionComponentRole.PRODUCT);
				rInput.addComponent(ric);
			}
			else
				return errors.size();
		}
		
		return 0;
	};
	
	private void readAutoFileHeader() {
		//TODO
	}
	
	private void readRDFileHeader() {
		String line = readLine();
		if (line == null || !line.startsWith("$RDFILE")) {
			errors.add("RDFile Header: Line " + curLineNum + " is missing or does not start with $RDFILE");
			return;
		}
		line = readLine();
		if (line == null || !line.startsWith("$DATM")) {
			errors.add("RDFile Header: Line " + curLineNum + " is missing or does not start with $DATM");
			return;
		}
	}
	
	private void readRXNFileHeader() {
		String line = readLine();
		if (line == null || !line.startsWith("$RXN")) {
			errors.add("RXN Header: Line " + curLineNum + " is missing or does not start with $RXN");
			return;
		}		
		line = readLine(); //Header Line 2 reaction name
		if (line == null) {
			errors.add("RXN Header (reaction name): Line " + curLineNum + " is missing");
			return;
		}
		line = readLine(); //Header Line 3 user name, program, date
		if (line == null) {
			errors.add("RXN Header (user name, progra, date,...): Line " + curLineNum + " is missing");
			return;
		}
		line = readLine(); //Header Line 4 comment
		if (line == null) {
			errors.add("RXN Header (comment or blank): Line " + curLineNum + " is missing");
			return;
		}
	}
	
	public void readRXNCountLine() {
		//Read RXN count line: rrrppp
		String line = readLine();		
		if (line == null) {
			errors.add("RXN counts Line " + curLineNum + " is missing !");
			return;
		}
		Integer rrr = readInteger(line, 0, 3);
		if (rrr == null || rrr < 0) {
			errors.add("RXN counts (rrrppp) Line  " + curLineNum + " : incorrect number of reagents (rrr part): " + line);
			return;
		}
		else
			numOfReagentsToRead = rrr;
		Integer ppp = readInteger(line, 3, 3);
		if (ppp == null || ppp < 0) {
			errors.add("RXN counts (rrrppp) Line  " + curLineNum + " : incorrect number of reagents (ppp part): " + line);
			return;
		}
		else
			numOfProductsToRead = ppp;
	}
	
	private void readMOLHeader() {
		String line = readLine();
		if (line == null || !line.startsWith("$MOL")) {
			errors.add(errorComponentContext + "MOL Start section in Line " 
					+ curLineNum + " is missing or does not start with $MOL" + " --> " + line);
			return;
		}
		line = readLine();
		if (line == null) {
			errors.add(errorComponentContext + "MOL Header (line 1) in Line" 
					+ curLineNum + " is missing");
			return;
		}
		line = readLine();
		if (line == null) {
			errors.add(errorComponentContext + "MOL Header (line 2) in Line" 
					+ curLineNum + " is missing");
			return;
		}
		line = readLine();
		if (line == null) {
			errors.add(errorComponentContext + "MOL Header (line 3) in Line" 
					+ curLineNum + " is missing");
			return;
		}
	}
	
	private void readMOLCountsLine() {
		//MOL Counts line: aaabbblllfffcccsssxxxrrrpppiiimmmvvvvvv
		String line = readLine();		
		if (line == null) {
			errors.add("MOL counts Line " + curLineNum + " is missing !");
			return;
		}
		Integer aaa = readInteger(line, 0, 3);
		if (aaa == null || aaa < 0) {
			errors.add("MOL counts (aaabbblll...) Line  " + curLineNum 
					+ " : incorrect number of atoms (aaa part): " + line);
			return;
		}
		else
			numOfAtomsToRead = aaa;
		Integer bbb = readInteger(line, 3, 3);
		if (bbb == null || bbb < 0) {
			errors.add("MOL counts (aaabbblll...) Line  " + curLineNum 
					+ " : incorrect number of bonds (bbb part): " + line);
			return;
		}
		else
			numOfBondsToRead = bbb;
	}
	
	private void readMOLCTABBlock(RinchiInputComponent ric) {
		for (int i = 0; i < numOfAtomsToRead; i++) {
			readMOLAtomLine(i, ric);
			if (!errors.isEmpty())
				return;
		}
		for (int i = 0; i < numOfBondsToRead; i++) {
			readMOLBondLine(i, ric);
			if (!errors.isEmpty())
				return;
		}
	}
	
	private void readMOLAtomLine(int atomIndex, RinchiInputComponent ric) {
		//Read MDL atom line
		//xxxxx.xxxxyyyyy.yyyyzzzzz.zzzz aaaddcccssshhhbbbvvvHHHrrriiimmmnnneee
		String line = readLine();
		if (line == null) {
			errors.add(errorComponentContext + "MOL atom # " + (atomIndex + 1) 
					+ " in Line " + curLineNum + " is missing !");
			return;
		}
		Double coordX = readMDLCoordinate(line, 0);
		if (coordX == null) {
			errors.add(errorComponentContext + "MOL atom # " + (atomIndex + 1) 
					+ " in Line " + curLineNum + " coordinate x error --> " + line);
			return;
		}
		Double coordY = readMDLCoordinate(line, 10);
		if (coordY == null) {
			errors.add(errorComponentContext + "MOL atom # " + (atomIndex + 1) 
					+ " in Line " + curLineNum + " coordinate y error --> " + line);
			return;
		}
		Double coordZ = readMDLCoordinate(line, 20);
		if (coordZ == null) {
			errors.add(errorComponentContext + "MOL atom # " + (atomIndex + 1) 
					+ " in Line " + curLineNum + " coordinate z error --> " + line);
			return;
		}
		String atSymbol = readString(line, 30, 4); //length 4 for: ' ' + aaa
		if (atSymbol == null) {
			errors.add(errorComponentContext + "MOL atom # " + (atomIndex + 1) 
					+ " in Line " + curLineNum + " atom symbol error --> " + line);
			return;
		}
		
		//Check atom symbol
		int atNum = PerTable.getAtomicNumberFromElSymbol(atSymbol);
		if (atNum == -1) {
			errors.add(errorComponentContext + "MOL atom # " + (atomIndex + 1) 
					+ " in Line " + curLineNum + " atom symbol error --> " + line);
			return;
		}
		
		//Check old CTAB charge style
		Integer chCode = readInteger(line, 36, 3);
		if (chCode == null || chCode < 0 || chCode > 7 ) {
			errors.add(errorComponentContext + "MOL atom # " + (atomIndex + 1) 
					+ " in Line " + curLineNum + " atom charge coding error --> " + line);
			return;
		}
		int charge = getChargeFromOldCTABCoding(chCode);
		
		
		InchiAtom atom = new InchiAtom(atSymbol, coordX, coordY, coordZ);
		ric.addAtom(atom);
		
		if (charge != 0)
			atom.setCharge(charge); //M  CHG molecule property takes precedence if present
	}
	
	private void readMOLBondLine(int bondIndex, RinchiInputComponent ric) {
		//Read MDL bond line
		//111222tttsssxxxrrrccc
		String line = readLine();		
		if (line == null) {
			errors.add(errorComponentContext + "MOL bond # " + (bondIndex + 1) 
					+ " in Line " + curLineNum + " is missing !");
			return;
		}
		Integer a1 = readInteger(line, 0, 3);
		if (a1 == null || a1 < 0 || a1 > numOfAtomsToRead) {
			errors.add("MOL counts (111222ttt...) Line  " + curLineNum 
					+ " : incorrect atom number (111 part): " + line);
			return;
		}
		Integer a2 = readInteger(line, 3, 3);
		if (a2 == null || a2 < 0 || a2 > numOfAtomsToRead) {
			errors.add("MOL counts (111222ttt...) Line  " + curLineNum 
					+ " : incorrect atom number (222 part): " + line);
			return;
		}
		Integer ttt = readInteger(line, 6, 3);
		if (ttt == null || ttt < 0 || ttt > 3) {
			errors.add("MOL counts (111222ttt...) Line  " + curLineNum 
					+ " : incorrect bond typer (ttt part): " + line);
			return;
		}		
		InchiBond bond = new InchiBond(ric.getAtom(a1-1), ric.getAtom(a2-1), InchiBondType.of((byte)ttt.intValue()));
		ric.addBond(bond);
	}
	
	private void readMOLPropertiesBlock(RinchiInputComponent ric) {
		String line = readLine();
		while (processPropertyLine(line, ric) == 0)
			line = readLine();
	}
	
	private int processPropertyLine(String line, RinchiInputComponent ric) {
		if (line == null || line.startsWith("M  END"))
			return -1;
		
		if (line.startsWith("M  ISO"))
			readIsotopePropertyLine (line, ric);
		
		return 0;
	}
	
	private int readIsotopePropertyLine(String line, RinchiInputComponent ric) {
		//MDL format for isotope line: 
		//M ISOnn8 aaa vvv ...
		
		Integer n = readInteger(line, 6, 3); //atom count
		if (n == null || n < 1 || n > 8) {
			errors.add("M ISO molecule property Line (M ISOnn8 aaa vvv ...) " + curLineNum 
					+ " : incorrect number of atoms (nn8 part): " + line);
			return -1;
		}
		
		int pos = 9;
		for (int i = 0; i < n; i++) {
			// aaa
			Integer atomIndex = readInteger(line, pos, 4);			
			if (atomIndex == null || atomIndex < 1 || atomIndex > ric.getAtoms().size()) {
				errors.add("M ISO molecule property Line (M ISOnn8 aaa vvv ...) " + curLineNum 
						+ " : incorrect atom index for (aaa vvv) pair #" + (i+1) + " :" + line);
				return -2;
			}
			pos += 4;
			// vvv
			Integer mass = readInteger(line, pos, 4);
			if (mass == null || mass < 1 ) {
				errors.add("M ISO molecule property Line (M ISOnn8 aaa vvv ...) " + curLineNum 
						+ " : incorrect mass for (aaa vvv) pair #" + (i+1) + " :" + line);
				return -3;
			}
			pos += 4;
			ric.getAtom(atomIndex-1).setIsotopicMass(mass);
		}		
		return 0;
	}
	
	private RinchiInputComponent readMDLMolecule() {
		RinchiInputComponent ric = new RinchiInputComponent();
		readMOLHeader();
		if (!errors.isEmpty())
			return null;
		
		readMOLCountsLine();
		if (!errors.isEmpty())
			return null;
		
		readMOLCTABBlock(ric);
		if (!errors.isEmpty())
			return null;
		
		readMOLPropertiesBlock(ric);
		if (!errors.isEmpty())
			return null;
		
		return ric;
	}
	
	
	private String readString(String line, int startPos, int lenght) {
		int endPos = startPos + lenght;
		if (startPos > line.length() || endPos > line.length())
			return null;
		String s = line.substring(startPos, endPos).trim();
		return s;
	}
	
	private Integer readInteger(String line, int startPos, int lenght) {
		int endPos = startPos + lenght;
		if (startPos > line.length() || endPos > line.length())
			return null;
		String s = line.substring(startPos, endPos).trim();
		try {
			int i = Integer.parseInt(s);
			return i;
		}
		catch(Exception x) {
			errors.add(errorPrefix() + "Error on parsing integer: " + s);
			return null;
		}
	}
	
	private Double readMDLCoordinate(String line, int startPos) {
		int endPos = startPos + MDL_FLOAT_SPACES;
		if (startPos > line.length() || endPos > line.length())
			return null;
		
		String s = line.substring(startPos, endPos).trim();
		if (line.charAt(startPos + 5) != '.') {
			errors.add(errorPrefix() + "Incorrect coordinate format: " + s);
			return null;
		}	
		
		try {
			double d = Double.parseDouble(s);
			return d;
		}
		catch(Exception x) {
			errors.add(errorPrefix() + "Error on parsing float: " + s);
			return null;
		}
	}
	
	private String errorPrefix() {
		return "Line " + curLineNum + ": "; 
	}
	
	
	public List<String> getErrors() {
		return errors;
	}
	
	public String getAllErrors() {
		StringBuilder sb = new StringBuilder();
		for (String err: errors)
			sb.append(err).append("\n");
		return sb.toString();
	}
	
	public ReactionFileFormat getFormat() {
		return format;
	}

	public void setFormat(ReactionFileFormat format) {
		if (format != null)
			this.format = format;
	}

	public boolean isAdd_M_ISO_Line() {
		return add_M_ISO_Line;
	}

	public void setAdd_M_ISO_Line(boolean add_M_ISO_Line) {
		this.add_M_ISO_Line = add_M_ISO_Line;
	}

	public boolean isAdd_M_CHG_Line() {
		return add_M_CHG_Line;
	}

	public void setAdd_M_CHG_Line(boolean add_M_CHG_Line) {
		this.add_M_CHG_Line = add_M_CHG_Line;
	}
		
}
