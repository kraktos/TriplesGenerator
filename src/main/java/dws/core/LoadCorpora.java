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
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import LBJ2.nlp.Sentence;
import LBJ2.nlp.Word;
import LBJ2.parse.LinkedChild;
import LBJ2.parse.LinkedVector;
import dws.util.SpotLinkDao;

/**
 * loads a given text corpus
 * 
 * @author adutta
 *
 */
public class LoadCorpora {

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

	/**
	 * 
	 */
	public LoadCorpora() {

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
			INPUT_TEXT_CORPUS = "/var/work/newsCorpora/eng_news_2005_30K-sentences.txt";
			TRIPLES_FILE = new File(INPUT_TEXT_CORPUS).getParent()
					+ "/triples.txt";

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

		try {

			br = new BufferedReader(new FileReader(inputTextCorpus));

			// go through the input corpora file line by line
			while ((sCurrentLine = br.readLine()) != null) {
				// split on one or more tab
				String[] sntncs = sCurrentLine.split("\t+");
				if (sntncs.length == 2) {
					sentence = new Sentence(sntncs[1]);

					// get the annotations
					annotatedSpots = annotate(sentence.text);
				}

				// check the annotations
				// debug point
				System.out.println("=========\n" + sentence.text);
				for (Entry<String, SpotLinkDao> spot : annotatedSpots
						.entrySet()) {
					System.out.println(spot.getKey() + " ==> "
							+ spot.getValue());
				}

				writeOutTriples(sentence, annotatedSpots, triplesWriter);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (triplesWriter != null)
				triplesWriter.close();
		}
	}

	/**
	 * write out the new facts
	 * 
	 * @param sentence
	 * @param annotatedSpots
	 * @param triplesWriter
	 * @throws IOException
	 */
	private static void writeOutTriples(Sentence sentence,
			Map<String, SpotLinkDao> annotatedSpots,
			BufferedWriter triplesWriter) throws IOException {

		StringBuilder build = null;
		List<String> path = new ArrayList<String>();
		List<String> matches = null;
		String input = sentence.text;

		Pattern pattern = null;
		Matcher matcher = null;

		// take the sentence and split into words
		LinkedVector wordsInSentence = getWords(sentence);

		boolean flag = false;
		for (int idx = 0; idx < wordsInSentence.size(); idx++) {
			LinkedChild word = wordsInSentence.get(idx);
			if (annotatedSpots.containsKey(word.toString())) {
				path.add(word.toString());
			}
		}

		if (path.size() >= 2) {
			int idx = 1;
			while (idx < path.size()) {
				try {
					pattern = Pattern.compile("(?<=\\b" + path.get(idx - 1)
							+ "\\b).*?(?=\\b" + path.get(idx) + "\\b)");
					matcher = pattern.matcher(input);
					matches = new ArrayList<String>();
					while (matcher.find()) {
						matches.add(matcher.group());
					}

					if (matches != null && matches.size() > 0) {
						triplesWriter.write("\n" + path.get(idx - 1) + ";\t");
						build = new StringBuilder();
						for (String s : matches) {
							build = build.append(s + " ");
						}
						triplesWriter.write(build.toString().trim() + ";\t"
								+ path.get(idx));
						triplesWriter.flush();
					}

				} catch (PatternSyntaxException e) {
					// ignore the pattern
				} finally {
					// increment
					idx = idx + 1;
				}
			}
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

	private static LinkedVector getWords(Sentence sentence) {
		LinkedVector lVector = new LinkedVector();
		String[] words = sentence.toString().split("\\s");
		for (int wrdIdx = 0; wrdIdx < words.length; wrdIdx++) {
			lVector.add(new Word(words[wrdIdx]));
		}
		return lVector;
	}

}
