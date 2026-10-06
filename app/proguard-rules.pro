# R8 rules of Bona vortaro, besides those of the libraries.
#
# The app uses no reflection: nothing to keep by name. If that changes,
# keep the classes concerned here.

# The stack traces of crashes stay readable: line numbers kept, the
# names of the source files hidden
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
