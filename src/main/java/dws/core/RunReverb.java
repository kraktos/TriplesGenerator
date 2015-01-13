/**
 * 
 */
package dws.core;

import it.acubelab.tagme.AnnotatedText;
import it.acubelab.tagme.Annotation;
import it.acubelab.tagme.Disambiguator;
import it.acubelab.tagme.RelatednessMeasure;
import it.acubelab.tagme.RhoMeasure;
import it.acubelab.tagme.Segmentation;
import it.acubelab.tagme.TagmeParser;
import it.acubelab.tagme.config.TagmeConfig;
import it.acubelab.tagme.preprocessing.TopicSearcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import LBJ2.nlp.Sentence;
import dws.util.SpotLinkDao;
import edu.washington.cs.knowitall.extractor.ReVerbExtractor;
import edu.washington.cs.knowitall.nlp.ChunkedSentence;
import edu.washington.cs.knowitall.nlp.OpenNlpSentenceChunker;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction;

/**
 * loads a given text corpus
 * 
 * @author adutta
 *
 */
public class RunReverb {

	// location of TagMe Configuration file
	private static final String CONFIG_FILE = "/var/work/tagme/tagme.acubelab/config.sample.en.xml";

	// language of choice
	private static final String LANG = "en";

	private static String INPUT_TEXT_CORPUS = null;
	private static String TRIPLES_FILE = null;

	private static final long FACTOR = 1000000000;

	static RelatednessMeasure rel = null;
	static TagmeParser parser = null;
	static Disambiguator disamb = null;
	static Segmentation segmentation = null;
	static RhoMeasure rho = null;
	static OpenNlpSentenceChunker chunker;
	static ReVerbExtractor reverb;

	static ChunkedSentence sent;

	/**
	 * 
	 */
	public RunReverb() {
		// Looks on the classpath for the default model files.
		try {
			chunker = new OpenNlpSentenceChunker();
			reverb = new ReVerbExtractor();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		long start = System.nanoTime();

		try {
			// Initiate entity tagger
			init();
		} catch (IOException e) {
			System.err.println("Exception while initiating repository");
			e.printStackTrace();
		}

		System.out.println("Time to init = " + (System.nanoTime() - start)
				/ FACTOR + " mints..");

		try {
			INPUT_TEXT_CORPUS = args[0];
			TRIPLES_FILE = new File(INPUT_TEXT_CORPUS).getParent()
					+ "/triples-" + args[1] + ".reverb.txt";

			readTrainingTextFile(INPUT_TEXT_CORPUS);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * init the TagMe with all the backend knowledge
	 * 
	 * @throws IOException
	 */
	private static void init() throws IOException {

		TagmeConfig.init(CONFIG_FILE);
		rel = RelatednessMeasure.create(LANG);
		parser = new TagmeParser(LANG, true);
		disamb = new Disambiguator(LANG);
		segmentation = new Segmentation();
		rho = new RhoMeasure();

	}

	/**
	 * loads a training file to annotate
	 * 
	 * @throws IOException
	 */
	private static void readTrainingTextFile(String inputTextCorpus)
			throws IOException {

		BufferedReader br = null;
		BufferedWriter triplesWriter = new BufferedWriter(new FileWriter(
				TRIPLES_FILE));

		Map<String, SpotLinkDao> annotatedSpots = null;
		String sCurrentLine = null;
		Sentence sentence = null;
		String[] sntncs = null;

		try {

			br = new BufferedReader(new FileReader(inputTextCorpus));

			System.out.println("Writing output..wait..");

			// go through the input corpora file line by line
			while ((sCurrentLine = br.readLine()) != null) {
				// split on one or more tab
				sntncs = sCurrentLine.split("\t+");
				if (sntncs.length == 2) {
					sentence = new Sentence(sntncs[1]);

					// get the annotations
					annotatedSpots = annotate(sentence.text);
				}

				extract(sentence, annotatedSpots, triplesWriter);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (triplesWriter != null)
				triplesWriter.close();
		}
	}

	/**
	 * @param test
	 * @param writer
	 * @return
	 * @throws IOException
	 */
	private static Map<String, SpotLinkDao> annotate(String test)
			throws IOException {

		SpotLinkDao dao = null;
		AnnotatedText ann_text = new AnnotatedText(test);

		Map<String, SpotLinkDao> mapSpots = new HashMap<String, SpotLinkDao>();

		parser.parse(ann_text);
		segmentation.segment(ann_text);
		disamb.disambiguate(ann_text, rel);
		rho.calc(ann_text, rel);

		List<Annotation> annots = ann_text.getAnnotations();
		TopicSearcher searcher = new TopicSearcher(LANG);

		for (Annotation a : annots) {
			if (a.isDisambiguated() && a.getRho() >= 0.1) {

				dao = new SpotLinkDao(searcher.getTitle(a.getTopic()),
						a.getTopic(), a.getRho(), "");

				mapSpots.put(ann_text.getOriginalText(a), dao);

			}
		}

		return mapSpots;
	}

	/**
	 * calls Reverb extraction routine
	 * 
	 * @param sentStr
	 * @param annotatedSpots
	 * @param triplesWriter
	 * @throws IOException
	 */
	private static void extract(Sentence sentStr,
			Map<String, SpotLinkDao> annotatedSpots,
			BufferedWriter triplesWriter) throws IOException {

		String arg1 = null;
		String reltn = null;
		String arg2 = null;

		sent = chunker.chunkSentence(sentStr.text);

		for (ChunkedBinaryExtraction extr : reverb.extract(sent)) {

			arg1 = extr.getArgument1().toString();
			reltn = extr.getRelation().toString();
			arg2 = extr.getArgument2().toString();

			if (annotatedSpots.containsKey(arg1)
					&& annotatedSpots.containsKey(arg2)) {
				triplesWriter.write(arg1 + "; " + reltn + "; " + arg2 + "\n");
				triplesWriter.flush();
			}
		}
	}
}
