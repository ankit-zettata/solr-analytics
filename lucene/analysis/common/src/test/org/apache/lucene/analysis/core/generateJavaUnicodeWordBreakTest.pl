#!/usr/bin/perl

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

use warnings;
use strict;
use File::Spec;
use Getopt::Long;
use LWP::UserAgent;

my ($volume, $directory, $script_name) = File::Spec->splitpath($0);

my $version = '';
unless (GetOptions("version=s" => \$version) && $version =~ /\d+\.\d+\.\d+/) {
  print STDERR "Usage: $script_name -v <version>\n";
  print STDERR "\tversion must be of the form X.Y.Z, e.g. 5.2.0\n"
      if ($version);
  exit 1;
}
my $url_prefix = "http://www.unicode.org/Public/${version}/ucd";
my $scripts_url = "${url_prefix}/Scripts.txt";
my $line_break_url = "${url_prefix}/LineBreak.txt";
my $word_break_url = "${url_prefix}/auxiliary/WordBreakProperty.txt";
my $word_break_test_url = "${url_prefix}/auxiliary/WordBreakTest.txt";
my $underscore_version = $version;
$underscore_version =~ s/\./_/g;
my $class_name = "WordBreakTestUnicode_${underscore_version}";
my $output_filename = "${class_name}.java";
my $header =<<"__HEADER__";
package org.apache.lucene.analysis.core;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.junit.Ignore;

/**
 * This class was automatically generated by ${script_name}
 * from: ${url_prefix}/auxiliary/WordBreakTest.txt
 *
 * WordBreakTest.txt indicates the points in the provided character sequences
 * at which conforming implementations must and must not break words.  This
 * class tests for expected token extraction from each of the test sequences
 * in WordBreakTest.txt, where the expected tokens are those character
 * sequences bounded by word breaks and containing at least one character
 * from one of the following character sets:
 *
 *    \\p{Script = Han}                (From $scripts_url)
 *    \\p{Script = Hiragana}
 *    \\p{LineBreak = Complex_Context} (From $line_break_url)
 *    \\p{WordBreak = ALetter}         (From $word_break_url)
 *    \\p{WordBreak = Katakana}
 *    \\p{WordBreak = Numeric}         (Excludes full-width Arabic digits)
 *    [\\uFF10-\\uFF19]                 (Full-width Arabic digits)
 */
\@Ignore
public class ${class_name} extends BaseTokenStreamTestCase {

  public void test(Analyzer analyzer) throws Exception {
__HEADER__

my $codepoints = [];
map { $codepoints->[$_] = 1 } (0xFF10..0xFF19);
# Complex_Context is an alias for 'SA', which is used in LineBreak.txt
# Using lowercase versions of property value names to allow for case-
# insensitive comparison with the names in the Unicode data files.
parse_Unicode_data_file($line_break_url, $codepoints, {'sa' => 1});
parse_Unicode_data_file($scripts_url, $codepoints, 
                        {'han' => 1, 'hiragana' => 1});
parse_Unicode_data_file($word_break_url, $codepoints,
                        {'aletter' => 1, 'katakana' => 1, 'numeric' => 1});
my @tests = split /\r?\n/, get_URL_content($word_break_test_url);

my $output_path = File::Spec->catpath($volume, $directory, $output_filename);
open OUT, ">$output_path"
  || die "Error opening '$output_path' for writing: $!";

print STDERR "Writing '$output_path'...";

print OUT $header;

for my $line (@tests) {
  next if ($line =~ /^\s*\#/);
  # ÷ 0001 × 0300 ÷  #  ÷ [0.2] <START OF HEADING> (Other) × [4.0] COMBINING GRAVE ACCENT (Extend_FE) ÷ [0.3]
  my ($sequence) = $line =~ /^(.*?)\s*\#/;
  print OUT "    // $line\n";
  $sequence =~ s/\s*÷\s*$//; # Trim trailing break character
  my $test_string = $sequence;
  $test_string =~ s/\s*÷\s*/\\u/g;
  $test_string =~ s/\s*×\s*/\\u/g;
  $test_string =~ s/\\u000A/\\n/g;
  $test_string =~ s/\\u000D/\\r/g;
  $sequence =~ s/^\s*÷\s*//; # Trim leading break character
  my @tokens = ();
  for my $candidate (split /\s*÷\s*/, $sequence) {
    my @chars = ();
    my $has_wanted_char = 0;
    while ($candidate =~ /([0-9A-F]+)/gi) {
      push @chars, $1;
      unless ($has_wanted_char) {
        $has_wanted_char = 1 if (defined($codepoints->[hex($1)]));
      }
    }
    if ($has_wanted_char) {
      push @tokens, '"'.join('', map { "\\u$_" } @chars).'"';
    }
  }
  print OUT "    assertAnalyzesTo(analyzer, \"${test_string}\",\n";
  print OUT "                     new String[] { ";
  print OUT join(", ", @tokens), " });\n\n";
}

print OUT "  }\n}\n";
close OUT;
print STDERR "done.\n";


# sub parse_Unicode_data_file
#
# Downloads and parses the specified Unicode data file, parses it, and
# extracts code points assigned any of the given property values, defining
# the corresponding array position in the passed-in target array.
#
# Takes in the following parameters:
#
#  - URL of the Unicode data file to download and parse
#  - Reference to target array
#  - Reference to hash of property values to get code points for
#
sub parse_Unicode_data_file {
  my $url = shift;
  my $target = shift;
  my $wanted_property_values = shift;
  my $content = get_URL_content($url);
  print STDERR "Parsing '$url'...";
  my @lines = split /\r?\n/, $content;
  for (@lines) {
    s/\s*#.*//;         # Strip trailing comments
    s/\s+$//;           # Strip trailing space
    next unless (/\S/); # Skip empty lines
    my ($start, $end, $property_value);
    if (/^([0-9A-F]{4,5})\s*;\s*(.+)/i) {
      # 00AA       ; LATIN
      $start = $end = hex $1;
      $property_value = lc $2; # Property value names are case-insensitive
    } elsif (/^([0-9A-F]{4,5})..([0-9A-F]{4,5})\s*;\s*(.+)/i) {
      # 0AE6..0AEF ; Gujarati
      $start = hex $1;
      $end = hex $2;
      $property_value = lc $3; # Property value names are case-insensitive
    } else {
      next;
    }
    if (defined($wanted_property_values->{$property_value})) {
      for my $code_point ($start..$end) {
        $target->[$code_point] = 1;
      }
    }
  }
  print STDERR "done.\n";
}

# sub get_URL_content
#
# Retrieves and returns the content of the given URL.
#
sub get_URL_content {
  my $url = shift;
  print STDERR "Retrieving '$url'...";
  my $user_agent = LWP::UserAgent->new;
  my $request = HTTP::Request->new(GET => $url);
  my $response = $user_agent->request($request);
  unless ($response->is_success) {
    print STDERR "Failed to download '$url':\n\t",$response->status_line,"\n";
    exit 1;
  }
  print STDERR "done.\n";
  return $response->content;
}
