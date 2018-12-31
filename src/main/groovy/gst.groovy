import org.codehaus.groovy.control.CompilerConfiguration
import org.freedesktop.gstreamer.Gst

import ai.kognition.config.SpringContextLoader
import ai.kognition.pilecv4j.gstreamer.*
import ai.kognition.pilecv4j.gstreamer.guard.GstScope
import ai.kognition.pilecv4j.gstreamer.util.*
import ai.kognition.video.controller.service.VideoFeedService

def usage() {
   println "groovy -cp classpath ${this.class.name} path-to-script"
   System.exit 1
}

class MainScript {
   private final VideoFeedService videoFeedService;

   MainScript(VideoFeedService vfs) {
      videoFeedService =  vfs;
   }

   def bin(Closure closure) {
      def binManager = new BinManager()
      def inlineDisplays = []

      BinManager.metaClass.rtsp { String uri -> videoFeedService.rtspSource(binManager,uri) }
      BinManager.metaClass.display { String title ->
         def id = new InlineDisplay()
         binManager.add(new BreakoutFilter("inline-display-" + title)
               .connect(id))
         inlineDisplays.add(id)
      }
      BinManager.metaClass.play {
         def pipe = binManager.buildPipeline()

         // Add the method "printDetails" to Pipeline
         pipe.getClass().metaClass.printDetails { GstUtils.printDetails(pipe) }

         pipe.play()
         inlineDisplays.forEach({ it.stopOnClose(pipe, null) })
         return pipe
      }

      closure.delegate = binManager
      closure.run()

      return binManager
   }
}

new GstScope(this.class.name, args).withCloseable( { scope ->
   SpringContextLoader.load(["spring/property-wiring.xml"] as String[], ["spring/main.xml"] as String[]).withCloseable( { ctx ->
      videoFeedService = ctx.getBean(VideoFeedService.class);

      // if (args.length != 1) usage()

      def compilerConfiguration = new CompilerConfiguration()
      compilerConfiguration.scriptBaseClass = DelegatingScript.class.name

      Binding binding = new Binding()

      def shell = new GroovyShell(binding, compilerConfiguration)

      binding.setProperty('args', args)

      def dslScript = shell.parse( new File(args[0]).text)

      dslScript.setDelegate(new MainScript(videoFeedService))
      dslScript.run()

      Gst.main();
   } )
})


