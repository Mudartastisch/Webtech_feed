
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.GregorianCalendar;
import java.util.Scanner;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

import jaxb.*;


/**
 * Servlet implementation class FeedModel
 */
@WebServlet("/Feed")
public class FeedModel extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public FeedModel() {
        super();
        feed = unserializeFeed();
        marshaller = createMarshaller(Feed.class, schema);
    }
    private final static File FEED_FILE =
            new File(FeedModel.class.getResource("feed.xml").getFile());

        // Constant file reference to your XSL style sheet
        private final static File XSL_SHEET = new File("xsl/feed.xsl");

        private static final Schema schema = loadSchema();
        private Feed feed;
        private final Marshaller marshaller;
        /**
         * @return reference to the feed
         */
        public Feed getFeed() {
            return feed;
        }

        
        public void validate(Object contentObject, @SuppressWarnings("rawtypes") Class contextPath) throws SAXException, IOException {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(contextPath);
                JAXBSource jaxbSource = new JAXBSource(jaxbContext, contentObject);
                Validator validator = schema.newValidator();
                validator.validate(jaxbSource);
            } catch (JAXBException ex) {
                throw new RuntimeException("Could not validate object", ex);
            }
        }

        /**
         * Gets a single entry by ID or null when no entry with that ID exists
         */
        public jaxb.Feed.Entry getEntry(String id) {
            // Please note that `feed.getEntry()` actually returns all the entries
            // of the feed, not only one.
            for (jaxb.Feed.Entry currentEntry : feed.getEntry()) {
                if (currentEntry.getId().equals(id)) {
                    return currentEntry;
                }
            }
            return null;
        }

        /**
         * Composes a new entry and adds it to the feed via `addEntry(entry)`
         */
        public String addEntry(String title, String url, String summary,
            String author)
        throws FileNotFoundException, IOException {
            Feed.Entry entry = new Feed.Entry();
            Feed.Entry.Link link = new Feed.Entry.Link();
            link.setHref(url);
            Feed.Entry.Author person = new Feed.Entry.Author();
            person.setName(author);

            String id = Integer.toHexString(url.hashCode());
            // Throw an exception, when the new entry is a duplicate of an
            // existing entry
            if (getEntry(id) != null) {
                throw new RuntimeException(
                    "Entry " + entry.getTitle() +
                    " with the same ID already exists."
                );
            }

            entry.setId(id);
            entry.setTitle(title);
            entry.setUpdated(getXMLGregorianCalendarNow());
            entry.setAuthor(person);
            entry.setLink(link);
            entry.setSummary(summary);
            try {
                validate(entry, Entry.class);
            } catch (SAXException ex) {
                throw new RuntimeException("Entry is not valid", ex);
            }

            // Update updated because we just altered the feed
            feed.setUpdated(getXMLGregorianCalendarNow());

            serializeFeed();

            return id;
        }



        
        /**
         * Get the current date and time as an instance of XMLGregorianCalendar
         *
         * @return current date
         */
        private static XMLGregorianCalendar getXMLGregorianCalendarNow() {
            try {
                DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

                return datatypeFactory.newXMLGregorianCalendar(
                    new GregorianCalendar()
                );
            } catch (DatatypeConfigurationException ex) {
                throw new RuntimeException(
                    "DatatypeFactory was not properly configured.", ex
                );
            }
        }

        /**
         * Loads and instantiates the projects XML Schema file
         *
         * @return reference to a Schema object
         */
        public static Schema loadSchema() {
            URL schemaFilePath = FeedModel.class.getResource("atom.xsd");

            try {
                return SchemaFactory
                    .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(schemaFilePath);
            } catch (SAXException ex) {
                throw new RuntimeException("Error during schema parsing", ex);
            } catch (NullPointerException ex) {
                throw new RuntimeException("Could not load Atom schema", ex);
            }
        }

        /**
         * Creates a properly configured Marshaller for serializing XML
         *
         * @param type Class of the used Java object that is represented
         * @param schema Schema to validate against when writing
         * @return the marshaller
         */
        private static Marshaller createMarshaller(@SuppressWarnings("rawtypes") Class type, Schema schema) {
            try {
                JAXBContext context = JAXBContext.newInstance(type);
                Marshaller marshaller = context.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
                marshaller.setSchema(schema);

                // Add the xml-stylesheet processing instruction
                String xslDeclaration = "<?xml-stylesheet type=\"text/xsl\" href=\""
                  + XSL_SHEET.toString() + "\"?>";
                marshaller.setProperty("com.sun.xml.internal.bind.xmlHeaders",
                    xslDeclaration);

                return marshaller;
            } catch (JAXBException ex) {
                throw new RuntimeException("Could not create Marshaller.", ex);
            }
        }

        /**
         * @return the feed
         */
        private static Feed unserializeFeed() {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Feed.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                unmarshaller.setSchema(schema);
                // Unserialize XML data into new Java content trees
                // This will also validate the XML data when the schema is set
                return unmarshaller
                    .unmarshal(new StreamSource(FEED_FILE), Feed.class)
                    .getValue();
            } catch (JAXBException ex) {
                throw new RuntimeException("Could not unserialize feed.", ex);
            }
        }

        public void serializeFeed() {
            try {
                try (PrintWriter writer = new PrintWriter(FEED_FILE)) {
                  serializeFeed(writer);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Could not serialized feed");
            }
        }

        /**
         * Serializes the Feed
         */
        public void serializeFeed(PrintWriter writer) {
            try {
                marshaller.marshal(feed, writer);
            } catch (JAXBException ex) {
                throw new RuntimeException("Could not serialize feed");
            }
        }

        /**
         * Serializes an Entry
         */
        public void serializeEntry(jaxb.Feed.Entry newEntry, PrintWriter writer) {
            try {
                marshaller.marshal(newEntry, writer);
            } catch (JAXBException ex) {
                throw new RuntimeException("Could not serialize entry");
            }
        }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/xml;charset=UTF-8");
		StringWriter outWriter = new StringWriter();
		StreamResult result = new StreamResult( outWriter );
		TransformerFactory factory = TransformerFactory.newInstance();
        Source xslt = new StreamSource(XSL_SHEET);
        Transformer transformer;
		try {
			transformer = factory.newTransformer(xslt);
			Source text = new StreamSource(FEED_FILE);
	        transformer.transform(text, result);
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}       
        
		StringBuffer sb = outWriter.getBuffer(); 
		String finalString = sb.toString();
		response.getWriter().write(finalString);
	}


}
