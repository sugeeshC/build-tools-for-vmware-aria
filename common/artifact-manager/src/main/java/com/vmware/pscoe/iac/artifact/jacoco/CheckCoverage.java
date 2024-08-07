package com.vmware.pscoe.iac.artifact.jacoco;

/*-
 * #%L
 * artifact-manager
 * %%
 * Copyright (C) 2023 - 2024 VMware
 * %%
 * Build Tools for VMware Aria
 * Copyright 2023 VMware, Inc.
 * 
 * This product is licensed to you under the BSD-2 license (the "License"). You may not use this product except in compliance with the BSD-2 License.
 * 
 * This product may include a number of subcomponents with separate copyright notices and license terms. Your use of these subcomponents is subject to the terms and conditions of the subcomponent's license, as noted in the LICENSE file.
 * #L%
 */

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CheckCoverage {


	public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
		String baseBranch = System.getenv("GITHUB_BASE_REF");
		String headBranch = System.getenv("GITHUB_HEAD_REF");
		double coverageThreshold = 50.0;
		String modulePath = "common/artifact-manager/";
		String jacocoReportPath = modulePath + "target/site/jacoco/jacoco.xml";

		System.out.println("Branch 12 : "+ baseBranch);
		System.out.println("Branch 14 : "+ headBranch);

		ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "--name-only", baseBranch + "..."+headBranch);
		processBuilder.redirectErrorStream(true);
		Process process = processBuilder.start();

		List<String> changedFiles = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))){
			String line;
			while ((line = reader.readLine()) != null){
				changedFiles.add(line);
			}
		}

		System.out.println("Changed files from git diff:");
		changedFiles.forEach(System.out::println);


		File jacocoReport = new File(jacocoReportPath);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		dbFactory.setValidating(false);
		dbFactory.setNamespaceAware(true);
		dbFactory.setFeature("http://xml.org/sax/features/namespaces", false);
		dbFactory.setFeature("http://xml.org/sax/features/validation", false);
		dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
		Document doc = dbBuilder.parse(jacocoReport);

		double totalCoverage = 0.0;
		int totalFiles = 0;

		StringBuilder fileCoverageBuilder = new StringBuilder();

//		System.out.println("Checking JaCoCo report at: "+ jacocoReportPath);

		NodeList packageList = doc.getElementsByTagName("package");

		for (int i=0; i< packageList.getLength(); i++){
			Element packageElement = (Element) packageList.item(i);
			NodeList classList = packageElement.getElementsByTagName("class");

			for (int j=0; j< classList.getLength(); j++){
				Element classElement = (Element) classList.item(j);
				String sourcefilename = classElement.getAttribute("sourcefilename");
				if(sourcefilename != null && !sourcefilename.isEmpty()){
					String fileName = modulePath + "src/main/java/"+ packageElement.getAttribute("name").replace('.','/') + '/' + sourcefilename;
//					System.out.println("Checking file: "+ fileName);
					if (changedFiles.contains(fileName)){
						Double lineCoverage = calculateLineCoverage(classElement);
						if (lineCoverage != null){
							totalFiles++;
							totalCoverage += lineCoverage;
							boolean isAboveOrEqualThreshold = lineCoverage >= coverageThreshold;
							System.out.println("File: "+ fileName);
							System.out.println(" - Line coverage: "+ String.format("%.2f", lineCoverage)+ "%");

							fileCoverageBuilder.append("| ")
								.append(fileName)
								.append(" |")
								.append(String.format("%.2f", lineCoverage))
								.append("% | ")
								.append(isAboveOrEqualThreshold ? "\u2705" : "\u274C")
								.append(" |\n");
						} else {
							System.out.println("Warning: No line coverage found for "+ fileName);
						}
					}
				}
			}
		}

		if(totalFiles > 0){
			double averageCoverage = totalCoverage/totalFiles;
			System.out.println("\nAverage coverage for changed files: "+ String.format("%.2f", averageCoverage)+ "%" );
			if (averageCoverage < coverageThreshold) {
				System.out.println("ERROR: Coverage for changed files (" + String.format("%.2f", averageCoverage) +
					"% is below the threshold of " + coverageThreshold + "%");
			}

		} else {
			System.out.println("No changed files found.");
		}

		System.out.println("::set-output name=overall::"+ String.format("%.2f", totalCoverage/ totalFiles));
		System.out.println("::set-output name=changed-files::"+ totalFiles);
		System.out.println("::set-output name=file-coverage::"+ fileCoverageBuilder.toString().trim());

//		setOutput("overall", String.format("%.2f", totalCoverage/totalFiles));
//		setOutput("changed-files", String.valueOf(totalFiles));
//		setOutput("file-coverage", fileCoverageBuilder.toString().trim());
	}

	private static Double calculateLineCoverage(Element classElement) {
		NodeList counters = classElement.getElementsByTagName("counter");
		int total = 0;
		int missed = 0;
		int covered = 0;
		for (int i=0; i< counters.getLength(); i++){
			Element counter = (Element) counters.item(i);
			if ("INSTRUCTION".equals(counter.getAttribute("type"))){
				missed += Integer.parseInt(counter.getAttribute("missed"));
				covered += Integer.parseInt(counter.getAttribute("covered"));
			}
		}
		if ((missed+covered) > 0) {
			return (covered/(double)(missed+covered))* 100;
		}
		return null;
	}

	private static void setOutput(String name, String value) throws IOException{
		File envFile = new File(System.getenv("GITHUB_ENV"));
		try (FileWriter writer = new FileWriter(envFile, true)) {
			writer.write(name + "=" + value + "\n");
		}
	}

}
