import eu.evops.maven.pluins.cucumber.parallel.reporting.JsonResultMerger;
import eu.evops.maven.pluins.cucumber.parallel.reporting.MergeException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jdom2.JDOMException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Created by n471306 on 19/07/2017.
 */
public class StreamingJSONFormatterTest {

    private String pomPath = String.format("%s/test-project/pom.xml", new File(".").getAbsoluteFile().getParent());
    private JsonResultMerger jsonResultMerger;
    private List<String> reports;
    private File outputFile;

    @Before
    public void setup() throws IOException, InterruptedException, XmlPullParserException, JDOMException, MergeException {
        executeTests();
        mergeJSONs();
    }

    @Test
    public void testReportsGeneratedUsingStreamingJSONFormatter() throws IOException {
        String jsonString = FileUtils.readFileToString(outputFile,"UTF-8");
        JSONArray json = new JSONArray(jsonString);
        assertEquals("The report generated does not contain 3 feature files",3, json.length());

        List<JSONObject> testCases = new ArrayList<>();

        for (Object o : json) {
            if(!(o instanceof JSONObject))
                throw new IllegalArgumentException();

            JSONObject jsonObject = (JSONObject) o;
            JSONArray elements = jsonObject.getJSONArray("elements");
            for (Object testCase : elements) {
                testCases.add((JSONObject) testCase);
            }
        }
        assertEquals("Report generated does not have data of 15 test cases. Streaming JSON results merger failed",15, testCases.size());
    }

    public void executeTests() throws IOException, InterruptedException {
        File testProjectTargetFolder= new File(String.format("%s/test-project/target", new File(".").getAbsoluteFile().getParent()));
        FileUtils.deleteDirectory(testProjectTargetFolder);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn",
                "clean",
                "integration-test",
                "-DuseEnhancedJsonReporting=true",
                "-f",
                pomPath);

        Process start = processBuilder.start();
        start.waitFor();
    }

    public int getThreadCount() throws JDOMException, IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader(pomPath));

        ArrayList<Plugin> pluginsList = (ArrayList<Plugin>) model.getBuild().getPlugins();
        Optional<Plugin> pluginsList1 = pluginsList.stream().filter(it-> it.getArtifactId().contains("cucumber-runner-maven-plugin")).findFirst();

        Xpp3Dom xpp = (Xpp3Dom) pluginsList1.get().getConfiguration();
        return Integer.parseInt(xpp.getChild("threadCount").getValue());
    }

    public void mergeJSONs() throws MergeException, JDOMException, XmlPullParserException, IOException {
        jsonResultMerger = new JsonResultMerger("combined.json");
        reports = new ArrayList<>();

        for (int i = 0; i < getThreadCount(); i++) {
            File reportFile = new File(String.format("%s/test-project/target/cucumber/threads/thread-%d/reports/report.json", new File(".").getAbsoluteFile().getParent(),i));
            reports.add(reportFile.getPath());
        }

        File outputFolder = FileUtils.getTempDirectory();
        outputFile = jsonResultMerger.merge(outputFolder, reports);
        outputFile.deleteOnExit();
    }
}
