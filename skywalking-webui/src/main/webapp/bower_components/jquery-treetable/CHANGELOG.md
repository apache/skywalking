## 3.2.0 - June 13, 2014

* [ENHANCEMENT] Simplified directory structure
* [ENHANCEMENT] Do not show outline around indenter (#141)
* [ENHANCEMENT] Use closure for better compatibility with other jQuery versions/plugins (#138)
* [ENHANCEMENT] Bowerified (#113)
* [BUG] Fix for nodes with undefined parent (#132)
* [BUG] Fix removeNode() not deleting node from parent (#131)

## 3.1.0 - October 14, 2013

* [FEATURE] Added branch sort API (#87)
* [FEATURE] Added removeNode function to completely remove a node and its descendants (#75)
* [ENHANCEMENT] Embed Base64 encoded theme images in CSS (#103)
* [ENHANCEMENT] Do not show child nodes on expand of a hidden node (#97)
* [BUG] Moving nodes around caused order of children to be reversed (#112)
* [BUG] Bootstrap v3 compatibility fix for indenter (#111)
* [BUG] D&D example code should not break when nested inside other table (#108)
* [BUG] Node expanders wouldn't render correctly when nodes added dynamically (#79)

## 3.0.2 - July 3, 2013

* [FEATURE] Add argument to treetable plugin function to force reinitialization (#65)
* [BUG] Prevent error when new rows have leading white space (#77)
* [BUG] Fix loadBranch case when new nodes end up at wrong position when added to a branch with already expanded child nodes (#73)

## 3.0.1 - April 6, 2013

* [FEATURE] Expand/collapse nodes using keyboard Return key (#27, #30)
* [FEATURE] Allow root nodes to be added to the tree with loadBranch (#56)
* [ENHANCEMENT] Prevent potential reinitialization (#58)
* [ENHANCEMENT] Add new nodes at the end of children (#52)
* [ENHANCEMENT] Use on/off instead of bind/unbind for ataching event handlers (as per jQuery preferred guidelines)
* [BUG] Nodes wouldn't initialize properly when using loadBranch (#55)

## 3.0.0 - February 14, 2013

* [ENHANCEMENT] Use 'treetable' everywhere instead of a mix between 'treeTable' and 'treetable'

## 3.0.0-rc.2 - February 10, 2013

NOTE: This is a release candidate. Please test this version and report any
issues found.

* [FEATURE] Added (optional) tt-branch data attribute for AJAX use case
* [FEATURE] Added loadBranch()/unloadBranch() functions for adding/removing nodes to the tree (AJAX)
* [FEATURE] Added onInitialized event
* [FEATURE] Added onNodeInitialized event (#21)
* [ENHANCEMENT] Split CSS file into two files, one with basic styling required in most cases (jquery.treetable.css) and a 'theme' CSS (jquery.treetable.theme.default.css) that styles the trees like the ones in the documentation (#51)
* [ENHANCEMENT] Made appropriate plugin methods chainable
* [BUG] Having an empty data-tt-parent-id attribute would result in an error
* [BUG] A hardcoded reference to 'ttId' prevented proper use of nodeIdAttr setting

## 3.0.0-rc.1 - February 3, 2013

NOTE: This is a release candidate. Please test this version and report any
issues found.

The treeTable plugin has been completely rewritten for this release. Please
review the documentation for changes in the way the plugin is used and the
options that are supported.

* [FEATURE] Added test suite to verify plugin behavior
* [ENHANCEMENT] Better adhere to jQuery plugin standards
* [ENHANCEMENT] Use 'data-' attributes for tree metadata (#48, #7)
* [ENHANCEMENT] Optimized performance of large trees (#41)
* [BUG] Do not pollute the jQuery namespace (#45)
* [REMOVED] Built-in persistence feature has been removed
* [REMOVED] IE6 support

## 2.3.1 - 18 January 2013

* [FEATURE] Keyboard accessibility (#27, #30)
* [FEATURE] Optionally persist expanded node state using $.cookie (requires
jquery-cookie plugin) (#19)
* [ENHANCEMENT] Passing options to $.cookie persistence (#31)
* [ENHANCEMENT] Use addClass('ui-helper-hidden') as suggested by whittet (#15)
* [ENHANCEMENT] Added manifest file for jQuery plugin registry
* [BUG] onNodeShow doesn't pass context (#28)
* [BUG] Fixed problems with multiple treeTables with identical rows on the
same page (#16)
* [BUG] Fixed parentOf() not using options.childPrefix (#16)
* [BUG] Prototype conflict (#14)
* [BUG] Wrong documentation for "initialState" (#11)
* [BUG] failed to reveal a node for a pitfall in parentOf (#8)

## 2.3.0 - 16 March 2010

* Added GPL-LICENSE. The jQuery treeTable plugin is now dual-licensed under both the MIT and GPLv2 license.
* Added reveal function to expand a tree to a given node.
* Verified compatibility with jQuery 1.4.2.

## 2.2.3 - 18 August 2009

* Further optimized performance by eliminating most calls to jQuery's css function

## 2.2.2 - 25 July 2009

* Optimized performance of tree initialization (with initialState is collapsed)
* Added option 'clickableNodeNames' to make entire node name clickable to expand branch
* Updated jQuery to version 1.3.2
* Updated jQuery UI components to version 1.7.2 (Core, Draggable, Droppable)

## 2.2.1 - 15 February 2009

* Updated jQuery to version 1.3.1
* Updated jQuery UI components to 1.6rc6 (Core, Draggable, Droppable)

## 2.2 - 18 January 2009

* Fixed expander icon not showing on lazy-initialized nodes
* Fixed drag and drop example code to work for tables within tables
* Updated jQuery to version 1.3.0
* Updated jQuery UI components to 1.6rc5 (Core, Draggable, Droppable)

## 2.1 - 16 November 2008

* Optimized for faster initial loading (Issue #1)
* Implemented lazy initialization of nodes (Issue #1)
* Added information about the order of the rows in the HTML table to the documentation (Issue 2)
* Added performance.html with an example of a large table with drag and drop

## 2.0 - 12 November 2008

* Renamed plugin from ActsAsTreeTable to treeTable
* Added a minified version of the source code (jquery.treeTable.min.js)
* Added appendBranchTo function for easier manipulation of the tree with drag & drop (see docs for an example)
* Added option 'childPrefix' to allow customisation of the prefix used for node classes (default: 'child-of-')
* Renamed option 'default_state' to 'initialState'
* Changed initialState from 'expanded' to 'collapsed'
* Refactored collapse/expand behavior
* Moved private function bodies to their respective public function

## 1.2 - 3 November 2008

* Added option 'default_state'
* Expose additional functions: collapse, expand and toggleBranch

## 1.1 - 21 October 2008

* Fix JavaScript errors in IE7 due to comma-madness
* Fix collapse/expand behavior in IE7

## 1.0 - 2 October 2008

* Public release
